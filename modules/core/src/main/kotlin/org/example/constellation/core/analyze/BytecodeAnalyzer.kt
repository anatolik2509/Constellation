package org.example.constellation.core.analyze

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.GraphStats
import org.example.constellation.core.model.MethodRef
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeKind
import org.example.constellation.core.model.ParseError
import org.example.constellation.core.util.Names
import org.objectweb.asm.Opcodes
import java.nio.file.Path

/**
 * Builds a [CodeGraph] from compiled classes.
 *
 * Nodes are analyzed classes plus referenced-but-missing classes (external).
 * Edges: direct inheritance, interface implementation, and aggregated method calls.
 */
class BytecodeAnalyzer(private val paths: List<Path>) {

    /** Everything read off the class files in the scanning phase. */
    private class ScanResult(
        val analyzed: Map<String, RawClassInfo>,
        /** Best-effort module name per analyzed class (from its class-file origin). */
        val modules: Map<String, String?>,
        /** Digit-excluded classes, kept only to attribute their call edges to the enclosing class. */
        val excluded: Map<String, RawClassInfo>,
        val skippedSynthetic: Int,
        val duplicatesSkipped: Int,
        val parseErrors: List<ParseError>,
    )

    private data class CallKey(val from: String, val to: String)

    fun analyze(): CodeGraph {
        val scan = scanClasses()

        val nodes = sortedMapOf<String, Node>() // internal name -> Node
        for (info in scan.analyzed.values) {
            nodes[info.name] = toNode(info, scan.modules[info.name])
        }

        val (edges, externals) = collectEdges(scan)
        for (external in externals) nodes[external] = toExternalNode(external)

        return CodeGraph(
            nodes = nodes.values.sortedBy { it.id },
            edges = edges,
            stats = GraphStats(
                analyzedClasses = scan.analyzed.size,
                skippedSynthetic = scan.skippedSynthetic,
                duplicatesSkipped = scan.duplicatesSkipped,
                parseErrors = scan.parseErrors,
                sources = paths.map { it.toString() },
            ),
        )
    }

    // ---------- scanning ----------

    private fun scanClasses(): ScanResult {
        val scanner = ClassScanner()
        val analyzed = LinkedHashMap<String, RawClassInfo>()
        val modules = HashMap<String, String?>()
        val excluded = LinkedHashMap<String, RawClassInfo>()
        var skippedSynthetic = 0
        var duplicatesSkipped = 0
        val parseErrors = mutableListOf<ParseError>()

        for (entry in ClassFileProvider(paths).entries()) {
            val info = try {
                scanner.scan(entry.bytes)
            } catch (e: RuntimeException) {
                parseErrors += ParseError(entry.origin, e.message ?: e.javaClass.simpleName)
                continue
            }
            if (info.access and Opcodes.ACC_SYNTHETIC != 0 || isExcludedClassName(info.name)) {
                skippedSynthetic++
                if (isExcludedClassName(info.name)) excluded[info.name] = info
                continue
            }
            if (analyzed.putIfAbsent(info.name, info) != null) {
                duplicatesSkipped++
            } else {
                modules[info.name] = ModuleNames.of(entry.origin)
            }
        }
        return ScanResult(analyzed, modules, excluded, skippedSynthetic, duplicatesSkipped, parseErrors)
    }

    // ---------- nodes ----------

    private fun toNode(info: RawClassInfo, module: String?): Node {
        val kind = kindOf(info.access)
        val dotName = Names.toDotName(info.name)
        return Node(
            id = dotName,
            kind = kind,
            pojo = kind == NodeKind.CLASS && PojoDetector.isPojo(info),
            pkg = Names.pkgOf(dotName),
            simpleName = Names.simpleNameOf(dotName),
            sourceFile = info.sourceFile,
            module = module,
            declaredMethods = info.methods.asSequence()
                .filter { it.access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) == 0 }
                .map { MethodRef(it.name, it.descriptor) }
                .distinct()
                .sortedWith(compareBy({ it.name }, { it.descriptor }))
                .toList(),
        )
    }

    private fun toExternalNode(internalName: String): Node {
        val dotName = Names.toDotName(internalName)
        return Node(
            id = dotName,
            kind = NodeKind.EXTERNAL,
            pkg = Names.pkgOf(dotName),
            simpleName = Names.simpleNameOf(dotName),
        )
    }

    private fun kindOf(access: Int): NodeKind = when {
        access and Opcodes.ACC_ANNOTATION != 0 -> NodeKind.ANNOTATION
        access and Opcodes.ACC_INTERFACE != 0 -> NodeKind.INTERFACE
        else -> NodeKind.CLASS
    }

    // ---------- edges ----------

    /** Finalized edges (dot names, sorted) plus the referenced-but-missing classes. */
    private fun collectEdges(scan: ScanResult): Pair<List<Edge>, Set<String>> {
        val collector = EdgeCollector(scan.analyzed)

        for (info in scan.analyzed.values) {
            info.superName?.let { collector.addSupertypeEdge(info.name, it) }
            collector.addTypeDeclarationEdges(info)
            collector.addCalls(info.name, info)
        }
        // Digit-excluded bodies (Kotlin/Java lambdas-as-classes, anonymous objects) emit no
        // nodes, but their call edges belong to the enclosing class and must not be lost.
        val owners = resolveOwners(scan.analyzed, scan.excluded)
        for (info in scan.excluded.values) {
            val owner = owners[info.name] ?: continue
            collector.addCalls(owner, info)
        }
        return collector.build() to collector.referencedExternals
    }

    /** Accumulates structural edges, aggregated calls, and referenced external classes. */
    private class EdgeCollector(private val analyzed: Map<String, RawClassInfo>) {

        private val structuralEdges = mutableListOf<Edge>()
        private val callMethods = mutableMapOf<CallKey, MutableSet<MethodRef>>()
        val referencedExternals = sortedSetOf<String>()

        /** Direct inheritance: never from java/lang/Object, never to self. */
        fun addSupertypeEdge(owner: String, superName: String) {
            if (superName == OBJECT || superName == owner) return
            structuralEdges += Edge(owner, superName, EdgeType.INHERITANCE)
            externalCandidate(superName)
        }

        /**
         * Interface edges from the class-file `interfaces[]` list.
         * Annotation types implicitly implement java/lang/annotation/Annotation — skipped.
         * For interfaces, `interfaces[]` holds *extended* interfaces (bytecode keeps
         * superName = java/lang/Object there); for classes these are implementations.
         */
        fun addTypeDeclarationEdges(info: RawClassInfo) {
            if (info.access and Opcodes.ACC_ANNOTATION != 0) return
            val edgeType =
                if (info.access and Opcodes.ACC_INTERFACE != 0) EdgeType.INHERITANCE
                else EdgeType.IMPLEMENTS
            for (iface in info.interfaces) {
                if (iface == info.name) continue
                structuralEdges += Edge(info.name, iface, edgeType)
                externalCandidate(iface)
            }
        }

        /** Aggregates method calls of [caller] per target class, self-edges excluded. */
        fun addCalls(caller: String, info: RawClassInfo) {
            for (call in info.calls) {
                if (call.owner == caller || call.owner.startsWith("[")) continue
                if (isExcludedClassName(call.owner)) continue
                // super(...) duplicates the INHERITANCE edge — drop it.
                if (call.name == "<init>" && call.owner == info.superName) continue
                callMethods.getOrPut(CallKey(caller, call.owner)) { methodSet() } +=
                    MethodRef(call.name, call.descriptor)
                externalCandidate(call.owner)
            }
        }

        fun build(): List<Edge> =
            (structuralEdges + callEdges()).asSequence()
                .filter { it.from != it.to }
                .map { it.copy(from = Names.toDotName(it.from), to = Names.toDotName(it.to)) }
                .distinct()
                .sortedWith(compareBy({ it.from }, { it.to }, { it.type }))
                .toList()

        private fun callEdges(): List<Edge> =
            callMethods.map { (key, methods) -> Edge(key.from, key.to, EdgeType.CALL, methods.toList()) }

        private fun externalCandidate(internal: String) {
            if (internal !in analyzed && !isExcludedClassName(internal)) referencedExternals += internal
        }

        private fun methodSet(): MutableSet<MethodRef> =
            sortedSetOf(compareBy({ it.name }, { it.descriptor }))
    }

    companion object {
        private const val OBJECT = "java/lang/Object"

        /**
         * Anonymous/local/synthetic bodies: the segment after the last `$` starts with a digit
         * (`Foo$1`, `MainKt$main$1`). Also applied when materializing external nodes.
         * Such classes emit no nodes, but their call edges are attributed to the enclosing
         * class — see [resolveOwners].
         */
        fun isExcludedClassName(internal: String): Boolean {
            val last = internal.substringAfterLast('$')
            return last.isNotEmpty() && last[0].isDigit()
        }

        /**
         * Maps each excluded class to the analyzed class that owns its call edges.
         * EnclosingMethod is the primary source; InnerClasses/NestHost serve as fallbacks.
         * Chained anonymous bodies (`Foo$1$1` inside `Foo$1`) resolve transitively;
         * anything that never reaches an analyzed class is dropped (edge source must be a node).
         */
        private fun resolveOwners(
            analyzed: Map<String, RawClassInfo>,
            excluded: Map<String, RawClassInfo>,
        ): Map<String, String> {
            val resolved = HashMap<String, String?>()
            fun resolve(name: String, visiting: MutableSet<String>): String? {
                if (resolved.containsKey(name)) return resolved[name]
                if (!visiting.add(name)) return null // defensive: enclosing chains are trees
                var owner: String? = null
                for (candidate in ownerCandidates(excluded.getValue(name))) {
                    owner = when {
                        candidate in analyzed -> candidate
                        candidate in excluded -> resolve(candidate, visiting)
                        else -> null
                    }
                    if (owner != null) break
                }
                resolved[name] = owner
                return owner
            }
            return excluded.keys.mapNotNull { name ->
                resolve(name, mutableSetOf())?.let { name to it }
            }.toMap()
        }

        private fun ownerCandidates(info: RawClassInfo): List<String> = listOfNotNull(
            info.enclosingMethod?.owner,
            info.innerOuterName,
            info.nestHost,
        ).distinct()
    }
}
