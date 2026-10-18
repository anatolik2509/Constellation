package org.example.constellation.core.api.model

/** Fully sorted by construction, so JSON output is deterministic. */
data class CodeGraph(
    val nodes: List<Node>,
    val edges: List<Edge>,
    val stats: GraphStats,
)
