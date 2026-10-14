package org.example.constellation.core.filter

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind

/**
 * Filter configuration driven by the UI.
 * Empty [packages] means "all packages"; a missing axis in a query means "all values".
 * [excludedPackages] wins over [packages] — it exists to keep runtime/stdlib noise
 * (java.*, kotlin.*, …) out of the picture even when externals are shown.
 */
data class FilterSpec(
    val kinds: Set<NodeCategory> = NodeCategory.entries.toSet(),
    val edgeTypes: Set<EdgeType> = EdgeType.entries.toSet(),
    val packages: List<String> = emptyList(),
    val excludedPackages: List<String> = emptyList(),
)

/**
 * Package patterns: a pattern selects the packages it matches **plus their subpackages**.
 * `com.foo` and `com.foo.*` are equivalent; `*` within a segment matches any
 * characters except `.` (so `com.foobar` never matches `com.foo`).
 */
object PackageMatcher {

    fun matches(pattern: String, pkg: String): Boolean {
        // Both `com.foo` and `com.foo.*` mean "the package and anything under it";
        // `com.foobar` must NOT match (segment boundary required).
        val base = pattern.removeSuffix(".*")
        val basePattern = toRegex(base)
        return basePattern.matches(pkg) || pkg.matches(Regex("${basePattern.pattern}\\..*"))
    }

    private fun toRegex(pattern: String): Regex {
        val sb = StringBuilder()
        for (ch in pattern) {
            if (ch == '*') sb.append("[^.]*") else sb.append(Regex.escape(ch.toString()))
        }
        return Regex(sb.toString())
    }
}

/** Applies a [FilterSpec] server-side; the UI only configures filters, never reimplements them. */
object GraphFilter {

    fun apply(graph: CodeGraph, spec: FilterSpec): CodeGraph {
        val packagesActive = spec.packages.isNotEmpty()
        val exclusionsActive = spec.excludedPackages.isNotEmpty()

        fun nodeOk(n: Node): Boolean =
            n.category in spec.kinds &&
                (!packagesActive || spec.packages.any { PackageMatcher.matches(it, n.pkg) }) &&
                (!exclusionsActive || spec.excludedPackages.none { PackageMatcher.matches(it, n.pkg) })

        val keptNodes = graph.nodes.filter(::nodeOk)
        val keptIds = keptNodes.asSequence().map { it.id }.toHashSet()
        val keptEdges = graph.edges.filter { e ->
            e.type in spec.edgeTypes && e.from in keptIds && e.to in keptIds
        }

        // Externals exist only through relations; drop the ones left with degree 0.
        val withDegree = keptEdges.asSequence().flatMap { sequenceOf(it.from, it.to) }.toHashSet()
        val finalNodes = keptNodes.filter { it.kind != NodeKind.EXTERNAL || it.id in withDegree }

        return CodeGraph(finalNodes, keptEdges, graph.stats)
    }
}
