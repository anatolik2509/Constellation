package org.example.constellation.core.model

/**
 * Where a class came from. Classes of the analyzed project know their module
 * and source set; everything else is a dependency class ([External]).
 */
sealed interface ClassOrigin {
    data class Project(val module: String, val sourceSet: String) : ClassOrigin
    data object External : ClassOrigin
}
