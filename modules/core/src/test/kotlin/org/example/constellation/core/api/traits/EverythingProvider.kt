package org.example.constellation.core.api.traits

import org.example.constellation.core.api.traits.spi.ClassContext
import org.example.constellation.core.api.traits.spi.ClassTraitProvider

object EverythingProvider : ClassTraitProvider {
    override val id = TraitId("test.everything")
    override val displayName = "Everything"

    override fun traitFor(ctx: ClassContext): ClassTrait? =
        if (ctx.name.startsWith("demo/")) Marker else null

    object Marker : ClassTrait {
        override val id = TraitId("test.everything")
    }
}
