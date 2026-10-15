package org.example.constellation.core.analyze

import org.example.constellation.core.analyze.internal.DescriptorParser
import org.example.constellation.core.model.ClassKind
import org.example.constellation.core.model.ClassOrigin
import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.GraphStats
import org.example.constellation.core.model.MethodSignature
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.ParseError
import org.example.constellation.core.model.Relation
import org.example.constellation.core.properties.ClassContext
import org.example.constellation.core.properties.PropertyRegistry
import org.example.constellation.core.util.Names

/**
 * Builds the closed code model ([CodeGraph]) from compiled project classes.
 * This is where the bytecode world ends: descriptors are parsed into
 * [MethodSignature]s, access flags become kinds and origins, and providers
 * attach properties.
 */
class ProjectAnalyzer {

    fun analyze(request: AnalysisRequest): CodeGraph {
        val scanner = ClassScanner()
        val analyzed = LinkedHashMap<String, ScannedClass>()
        val excluded = LinkedHashMap<String, RawClassInfo>()
        var skippedSynthetic = 0
        var duplicatesSkipped = 0
        val parseErrors = mutableListOf<ParseError>()

        for (entry in ClassFileProvider(request.roots).entries()) {
            val info = try {
                scanner.scan(entry.bytes)
            } catch (e: RuntimeException) {
                parseErrors += ParseError(entry.origin, e.message ?: e.javaClass.simpleName)
                continue
            }
            if (info.access and SYNTHETIC != 0 || isExcludedClassName(info.name)) {
                skippedSynthetic++
                if (isExcludedClassName(info.name)) excluded[info.name] = info
                continue
            }
            if (analyzed.putIfAbsent(info.name, ScannedClass(info, entry.root)) != null) {
                duplicatesSkipped++
            }
        }

        val nodes = sortedMapOf<String, Node>() // internal name -> Node
        for (scanned in analyzed.values) {
            nodes[scanned.info.name] = toNode(scanned, request.registry)
        }

        val (edges, externals) = collectEdges(analyzed, excluded)
        for (external in externals) nodes[external] = toExternalNode(external)

        return CodeGraph(
            nodes = nodes.values.sortedBy { it.id },
            edges = edges,
            stats = GraphStats(
                analyzedClasses = analyzed.size,
                skippedSynthetic = skippedSynthetic,
                duplicatesSkipped = duplicatesSkipped,
                parseErrors = parseErrors,
                sources = request.roots.map { it.dir.toString() },
            ),
        )
    }

    private class ScannedClass(val info: RawClassInfo, val root: ClassRoot)

    private fun toNode(scanned: ScannedClass, registry: PropertyRegistry): Node {
        val info = scanned.info
        val dotName = Names.toDotName(info.name)
        return Node(
            id = dotName,
            kind = kindOf(info),
            origin = ClassOrigin.Project(module = scanned.root.module, sourceSet = scanned.root.sourceSet),
            pkg = Names.pkgOf(dotName),
            simpleName = Names.simpleNameOf(dotName),
            sourceFile = info.sourceFile,
            declaredMethods = info.methods.asSequence()
                .filter { it.access and (SYNTHETIC or BRIDGE) == 0 }
                .map { DescriptorParser.parseMethod(it.name, it.descriptor) }
                .distinct()
                .sortedWith(compareBy({ it.name }, { it.renderFull() }))
                .toList(),
            properties = registry.detectAll(ClassContext(info)),
        )
    }

    private fun toExternalNode(internalName: String): Node {
        val dotName = Names.toDotName(internalName)
        return Node(
            id = dotName,
            // The class was not analyzed, so its structural kind is unknown; CLASS is the
            // neutral approximation — consumers distinguish externals via origin anyway.
            kind = ClassKind.CLASS,
            origin = ClassOrigin.External,
            pkg = Names.pkgOf(dotName),
            simpleName = Names.simpleNameOf(dotName),
            sourceFile = null,
            declaredMethods = emptyList(),
            properties = emptyList(),
        )
    }

    private fun kindOf(info: RawClassInfo): ClassKind = when {
        info.access and ANNOTATION != 0 -> ClassKind.ANNOTATION
        info.access and INTERFACE != 0 -> ClassKind.INTERFACE
        else -> ClassKind.CLASS
    }

    // ---------- edges ----------

    /** Finalized edges (dot names, sorted) plus the referenced-but-missing classes. */
    private fun collectEdges(
        analyzed: Map<String, ScannedClass>,
        excluded: Map<String, RawClassInfo>,
    ): Pair<List<Edge>, Set<String>> {
        val collector = EdgeCollector(analyzed.keys)

        for (scanned in analyzed.values) {
            val info = scanned.info
            info.superName?.let { collector.addSupertypeEdge(info.name, it) }
            collector.addTypeDeclarationEdges(info)
            collector.addCalls(info.name, info)
        }
        // Digit-excluded bodies (Kotlin/Java lambdas-as-classes, anonymous objects) emit no
        // nodes, but their call edges belong to the enclosing class and must not be lost.
        val owners = resolveOwners(analyzed.keys, excluded)
        for (info in excluded.values) {
            val owner = owners[info.name] ?: continue
            collector.addCalls(owner, info)
        }
        return collector.build() to collector.referencedExternals
    }

    /** Accumulates structural edges, aggregated calls, and referenced external classes. */
    private class EdgeCollector(private val analyzedNames: Set<String>) {

        private val structuralEdges = mutableListOf<Edge>()
        private val callMethods = mutableMapOf<CallKey, MutableSet<MethodSignature>>()
        val referencedExternals = sortedSetOf<String>()

        /** Direct inheritance: never from java/lang/Object, never to self. */
        fun addSupertypeEdge(owner: String, superName: String) {
            if (superName == OBJECT || superName == owner) return
            structuralEdges += Edge(owner, superName, Relation.EXTENDS)
            externalCandidate(superName)
        }

        /**
         * Interface edges from the class-file `interfaces[]` list.
         * Annotation types implicitly implement java/lang/annotation/Annotation — skipped.
         * For interfaces, `interfaces[]` holds *extended* interfaces (bytecode keeps
         * superName = java/lang/Object there); for classes these are implementations.
         */
        fun addTypeDeclarationEdges(info: RawClassInfo) {
            if (info.access and ANNOTATION != 0) return
            val relation =
                if (info.access and INTERFACE != 0) Relation.EXTENDS
                else Relation.IMPLEMENTS
            for (iface in info.interfaces) {
                if (iface == info.name) continue
                structuralEdges += Edge(info.name, iface, relation)
                externalCandidate(iface)
            }
        }

        /** Aggregates method calls of [caller] per target class, self-edges excluded. */
        fun addCalls(caller: String, info: RawClassInfo) {
            for (call in info.calls) {
                if (call.owner == caller || call.owner.startsWith("[")) continue
                if (isExcludedClassName(call.owner)) continue
                // super(...) duplicates the EXTENDS edge — drop it.
                if (call.name == "<init>" && call.owner == info.superName) continue
                callMethods.getOrPut(CallKey(caller, call.owner)) { methodSet() } +=
                    DescriptorParser.parseMethod(call.name, call.descriptor)
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
            callMethods.map { (key, methods) -> Edge(key.from, key.to, Relation.CALLS, methods.toList()) }

        private fun externalCandidate(internal: String) {
            if (internal !in analyzedNames && !isExcludedClassName(internal)) referencedExternals += internal
        }

        private fun methodSet(): MutableSet<MethodSignature> =
            sortedSetOf(compareBy({ it.name }, { it.renderFull() }))
    }

    private data class CallKey(val from: String, val to: String)

    companion object {
        private const val OBJECT = "java/lang/Object"
        private const val SYNTHETIC = 0x1000
        private const val BRIDGE = 0x0040
        private const val INTERFACE = 0x0200
        private const val ANNOTATION = 0x2000

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
            analyzedNames: Set<String>,
            excluded: Map<String, RawClassInfo>,
        ): Map<String, String> {
            val resolved = HashMap<String, String?>()
            fun resolve(name: String, visiting: MutableSet<String>): String? {
                if (resolved.containsKey(name)) return resolved[name]
                if (!visiting.add(name)) return null // defensive: enclosing chains are trees
                var owner: String? = null
                for (candidate in ownerCandidates(excluded.getValue(name))) {
                    owner = when {
                        candidate in analyzedNames -> candidate
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
