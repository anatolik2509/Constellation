package org.example.constellation.core.properties

/** Stable property identifier, owner-prefixed: `constellation.data-model`. */
@JvmInline
value class PropertyId(val value: String) {
    override fun toString(): String = value
}
