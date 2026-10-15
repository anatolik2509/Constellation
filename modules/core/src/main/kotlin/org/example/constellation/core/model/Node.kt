package org.example.constellation.core.model

import org.example.constellation.core.properties.ClassProperty

/**
 * A class in the code graph.
 *
 * Additional classifications ([properties]) come from property providers and are
 * part of the model's self-description, not of its bytecode past.
 */
data class Node(
    /** Dot-separated name, e.g. `com.foo.Bar` (nested classes keep `$`). */
    val id: String,
    val kind: ClassKind,
    val origin: ClassOrigin,
    /** Package part of [id]; empty string for the default package. */
    val pkg: String,
    /** Last `$`- or `.`-segment of [id]. */
    val simpleName: String,
    /** Source file name if known; null for external classes. */
    val sourceFile: String? = null,
    /** Declared methods, sorted. */
    val declaredMethods: List<MethodSignature> = emptyList(),
    /** Provider-assigned properties (e.g. DataModel), sorted by id. */
    val properties: List<ClassProperty> = emptyList(),
) {
    val isExternal: Boolean get() = origin == ClassOrigin.External
}
