package org.example.constellation.core.properties

/**
 * A property a class carries. Open on purpose: this is the extension point for
 * third-party providers. Ids are immutable once shipped; payloads evolve
 * additively only.
 */
interface ClassProperty {
    val id: PropertyId
}
