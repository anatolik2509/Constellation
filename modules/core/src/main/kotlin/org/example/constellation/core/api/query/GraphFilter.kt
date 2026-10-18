package org.example.constellation.core.api.query

import org.example.constellation.core.api.model.CodeGraph
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.MethodInfo
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.Relation

/**
 * Returns the sub-graph of nodes passing [nodes] and edges whose type passes
 * [edges] and both of whose endpoints survive; external nodes left with no
 * relations are pruned (they exist only through relations).
 */
fun CodeGraph.filter(
    nodes: NodePredicate = NodePredicates.all(),
    edges: EdgePredicate = EdgePredicates.all(),
): CodeGraph {
    val keptNodes = this.nodes.filter(nodes::test)
    val keptIds = keptNodes.asSequence().map { it.id }.toHashSet()
    val keptEdges = this.edges.filter { edge ->
        edges.test(edge) && edge.from in keptIds && edge.to in keptIds
    }
    return pruneOrphanExternals(keptNodes, keptEdges)
}

/**
 * Trims call records failing [keep] and drops call edges left with no records
 * (structural edges always survive); external nodes left without relations
 * are pruned by the same rule as [filter].
 */
fun CodeGraph.filterCalls(keep: (MethodInfo) -> Boolean): CodeGraph {
    val trimmed = edges.map { edge ->
        if (!edge.type.isCallRelation()) edge else edge.copy(methods = edge.methods.filter(keep))
    }
    return pruneOrphanExternals(nodes, trimmed.filter { !it.type.isCallRelation() || it.methods.isNotEmpty() })
}

private fun Relation.isCallRelation(): Boolean = this == Relation.CALLS || this == Relation.CALLS_STATIC

private fun CodeGraph.pruneOrphanExternals(nodes: List<Node>, edges: List<Edge>): CodeGraph {
    val withDegree = edges.asSequence().flatMap { sequenceOf(it.from, it.to) }.toHashSet()
    val finalNodes = nodes.filter { !it.isExternal || it.id in withDegree }
    return CodeGraph(finalNodes, edges, stats)
}
