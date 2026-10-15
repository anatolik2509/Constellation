package org.example.constellation.core.properties

/**
 * Detects a property on a class. Returns null when the class does not carry it.
 * Implementations must be pure functions of [ClassContext].
 */
interface ClassPropertyProvider {
    val id: PropertyId

    /** Human-readable name shown by UIs as-is. */
    val displayName: String

    fun propertiesFor(ctx: ClassContext): ClassProperty?
}
