package org.example.constellation.core.analyze

internal class RawCall(
    val owner: String,
    val name: String,
    val descriptor: String,
    /** `invokestatic` dispatch (or a method reference to a static method). */
    val isStatic: Boolean,
)
