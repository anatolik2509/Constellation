package org.example.constellation.core.query

import org.example.constellation.core.model.CodeGraph

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
    val withDegree = keptEdges.asSequence().flatMap { sequenceOf(it.from, it.to) }.toHashSet()
    val finalNodes = keptNodes.filter { !it.isExternal || it.id in withDegree }
    return CodeGraph(finalNodes, keptEdges, stats)
}
