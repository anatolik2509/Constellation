package org.example.constellation.core.api.model

data class GraphStats(
    val analyzedClasses: Int,
    val skippedSynthetic: Int,
    val duplicatesSkipped: Int,
    val parseErrors: List<ParseError>,
    /** Analyzed root directories, for the meta panel. */
    val sources: List<String>,
)
