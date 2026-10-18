package org.example.constellation.core.api.traits.spi

/** One declared method as seen by the analyzer — technical, providers-only. */
class MethodDecl(
    val name: String,
    val descriptor: String,
    val isStatic: Boolean,
    val isSynthetic: Boolean,
)
