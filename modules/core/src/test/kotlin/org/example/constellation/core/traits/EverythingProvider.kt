package org.example.constellation.core.traits

object EverythingProvider : ClassTraitProvider {
    override val id = TraitId("test.everything")
    override val displayName = "Everything"

    override fun traitFor(ctx: ClassContext): ClassTrait? =
        if (ctx.name.startsWith("demo/")) Marker else null

    object Marker : ClassTrait {
        override val id = TraitId("test.everything")
    }
}
