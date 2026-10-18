package org.example.constellation.core.traits

/**
 * A trait a class carries. Open on purpose: this is the extension point for
 * third-party providers. Ids are immutable once shipped; payloads evolve
 * additively only.
 */
interface ClassTrait {
    val id: TraitId
}
