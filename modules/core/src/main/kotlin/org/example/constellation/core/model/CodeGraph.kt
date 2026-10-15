package org.example.constellation.core.model

/** A class file the analyzer failed to parse; [origin] is the path it came from. */
data class ParseError(val origin: String, val message: String)

data class GraphStats(
    val analyzedClasses: Int,
    val skippedSynthetic: Int,
    val duplicatesSkipped: Int,
    val parseErrors: List<ParseError>,
    /** Analyzed root directories, for the meta panel. */
    val sources: List<String>,
)

/** Fully sorted by construction, so JSON output is deterministic. */
data class CodeGraph(
    val nodes: List<Node>,
    val edges: List<Edge>,
    val stats: GraphStats,
)
