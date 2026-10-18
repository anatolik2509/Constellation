package org.example.constellation.core.api.traits.spi

import org.example.constellation.core.api.traits.ClassTrait
import org.example.constellation.core.api.traits.TraitId

/**
 * Detects a trait on a class. Returns null when the class does not carry it.
 * Implementations must be pure functions of [ClassContext].
 */
interface ClassTraitProvider {
    val id: TraitId

    /** Human-readable name shown by UIs as-is. */
    val displayName: String

    fun traitFor(ctx: ClassContext): ClassTrait?
}
