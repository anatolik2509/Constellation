package org.example.constellation.core.properties

/**
 * The class is a static component: a namespaced/singleton unit rather than an
 * instantiated participant of the object graph.
 */
data class StaticComponent(
    val carrier: CarrierKind,
) : ClassProperty {
    enum class CarrierKind { OBJECT, STATIC_NESTED, STATIC_HOLDER }

    override val id: PropertyId get() = ID

    companion object {
        val ID = PropertyId("constellation.static-component")
    }
}
