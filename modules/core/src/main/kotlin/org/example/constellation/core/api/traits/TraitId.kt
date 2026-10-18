package org.example.constellation.core.api.traits

/** Stable trait identifier, owner-prefixed: `constellation.data-model`. */
@JvmInline
value class TraitId(val value: String) {
    override fun toString(): String = value
}
