package org.example.constellation.core.properties

/** One declared method as seen by the analyzer — technical, providers-only. */
class MethodDecl(
    val name: String,
    val descriptor: String,
    val isStatic: Boolean,
    val isSynthetic: Boolean,
)

/** One declared static field as seen by the analyzer — technical, providers-only. */
class StaticFieldDecl(val name: String, val descriptor: String)
