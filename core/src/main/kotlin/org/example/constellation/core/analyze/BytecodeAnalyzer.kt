package org.example.constellation.core.analyze

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.GraphStats
import org.example.constellation.core.model.MethodRef
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeKind
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

    fun analyze(): CodeGraph {
        val scanner = ClassScanner()
        val analyzed = LinkedHashMap<String, RawClassInfo>()
        /** Digit-excluded classes, kept only to attribute their call edges to the enclosing class. */
        val excluded = LinkedHashMap<String, RawClassInfo>()
        var skippedSynthetic = 0
        var duplicatesSkipped = 0
        var parseErrors = 0

        for (entry in ClassFileProvider(paths).entries()) {
            val info = try {
                scanner.scan(entry.bytes)
            } catch (_: RuntimeException) {
                parseErrors++
                continue
            }
            if (info.access and Opcodes.ACC_SYNTHETIC != 0 || isExcludedClassName(info.name)) {
                skippedSynthetic++
                if (isExcludedClassName(info.name)) {
                    excluded[info.name] = info
                }
                continue
            }
            if (analyzed.putIfAbsent(info.name, info) != null) {
                duplicatesSkipped++
            }
        }

        val nodes = sortedMapOf<String, Node>() // internal name -> Node
        for ((name, info) in analyzed) {
            val kind = when {
                info.access and Opcodes.ACC_ANNOTATION != 0 -> NodeKind.ANNOTATION
                info.access and Opcodes.ACC_INTERFACE != 0 -> NodeKind.INTERFACE
                else -> NodeKind.CLASS
            }
            val pojo = kind == NodeKind.CLASS && PojoDetector.isPojo(info)
            val dot = Names.toDotName(name)
            nodes[name] = Node(
                id = dot,
                kind = kind,
                pojo = pojo,
                pkg = Names.pkgOf(dot),
                simpleName = Names.simpleNameOf(dot),
                sourceFile = info.sourceFile,
                declaredMethods = info.methods.asSequence()
                    .filter { it.access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) == 0 }
                    .map { MethodRef(it.name, it.descriptor) }
                    .distinct()
                    .sortedWith(compareBy({ it.name }, { it.descriptor }))
                    .toList(),
            )
        }

        val referenced = sortedSetOf<String>()
        fun externalCandidate(internal: String) {
            if (internal !in analyzed && !isExcludedClassName(internal)) referenced += internal
        }

        data class CallKey(val from: String, val to: String)

        val edges = mutableListOf<Edge>()
        val callMethods = mutableMapOf<CallKey, MutableSet<MethodRef>>()

        for ((name, info) in analyzed) {
            val isAnnotation = info.access and Opcodes.ACC_ANNOTATION != 0

            val sup = info.superName
            if (sup != null && sup != OBJECT && sup != name) {
                edges += Edge(name, sup, EdgeType.INHERITANCE)
                externalCandidate(sup)
            }
            // Annotation types implicitly implement java/lang/annotation/Annotation — not a real edge.
            if (!isAnnotation) {
                // For interfaces, `interfaces[]` holds *extended* interfaces (bytecode keeps
                // superName = java/lang/Object there); for classes these are implementations.
                val ifaceEdgeType =
                    if (info.access and Opcodes.ACC_INTERFACE != 0) EdgeType.INHERITANCE
                    else EdgeType.IMPLEMENTS
                for (iface in info.interfaces) {
                    if (iface == name) continue
                    edges += Edge(name, iface, ifaceEdgeType)
                    externalCandidate(iface)
                }
            }
            for (call in info.calls) {
                if (call.owner == name || call.owner.startsWith("[")) continue
                if (isExcludedClassName(call.owner)) continue
                // super(...) duplicates the INHERITANCE edge — drop it.
                if (call.name == "<init>" && call.owner == info.superName) continue
                callMethods.getOrPut(CallKey(name, call.owner)) {
                    sortedSetOf(compareBy({ it.name }, { it.descriptor }))
                } += MethodRef(call.name, call.descriptor)
                externalCandidate(call.owner)
            }
        }
        // Digit-excluded bodies (Kotlin/Java lambdas-as-classes, anonymous objects) emit no nodes,
        // but their call edges belong to the enclosing class and must not be lost.
        val owners = resolveOwners(analyzed, excluded)
        for ((name, info) in excluded) {
            val owner = owners[name] ?: continue
            for (call in info.calls) {
                if (call.owner == owner || call.owner.startsWith("[")) continue
                if (isExcludedClassName(call.owner)) continue
                // The synthetic's own super(...) is not a call of the enclosing class.
                if (call.name == "<init>" && call.owner == info.superName) continue
                callMethods.getOrPut(CallKey(owner, call.owner)) {
                    sortedSetOf(compareBy({ it.name }, { it.descriptor }))
                } += MethodRef(call.name, call.descriptor)
                externalCandidate(call.owner)
            }
        }
        for ((key, methods) in callMethods) {
            edges += Edge(key.from, key.to, EdgeType.CALL, methods.toList())
        }

        for (ext in referenced) {
            val dot = Names.toDotName(ext)
            nodes[ext] = Node(
                id = dot,
                kind = NodeKind.EXTERNAL,
                pkg = Names.pkgOf(dot),
                simpleName = Names.simpleNameOf(dot),
            )
        }

        val finalEdges = edges.asSequence()
            .filter { it.from != it.to }
            .map { it.copy(from = Names.toDotName(it.from), to = Names.toDotName(it.to)) }
            .distinct()
            .sortedWith(compareBy({ it.from }, { it.to }, { it.type }))
            .toList()

        return CodeGraph(
            nodes = nodes.values.sortedBy { it.id },
            edges = finalEdges,
            stats = GraphStats(
                analyzedClasses = analyzed.size,
                skippedSynthetic = skippedSynthetic,
                duplicatesSkipped = duplicatesSkipped,
                parseErrors = parseErrors,
                sources = paths.map { it.toString() },
            ),
        )
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
