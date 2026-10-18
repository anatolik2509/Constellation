package org.example.constellation.core.traits

/**
 * The class is a static component: a namespaced/singleton unit rather than an
 * instantiated participant of the object graph.
 */
data class StaticComponent(
    val carrier: CarrierKind,
) : ClassTrait {
    enum class CarrierKind { OBJECT, STATIC_NESTED, STATIC_HOLDER }

    override val id: TraitId get() = ID

    companion object {
        val ID = TraitId("constellation.static-component")
    }
}
