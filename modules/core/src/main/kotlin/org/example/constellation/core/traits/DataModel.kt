package org.example.constellation.core.traits

/**
 * The class is a data carrier: it exists to hold values, not to behave.
 * Replaces the old "POJO" notion and covers the whole family.
 */
data class DataModel(
    val carrier: CarrierKind,
    /** Fields / record components the carrier is built from. */
    val components: List<String>,
) : ClassTrait {
    enum class CarrierKind { JAVA_BEAN, KOTLIN_DATA_CLASS, JAVA_RECORD, ENUM }

    override val id: TraitId get() = ID

    companion object {
        val ID = TraitId("constellation.data-model")
    }
}
