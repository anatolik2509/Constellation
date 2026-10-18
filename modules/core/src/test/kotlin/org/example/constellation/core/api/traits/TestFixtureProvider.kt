package org.example.constellation.core.api.traits

import org.example.constellation.core.api.traits.spi.ClassContext
import org.example.constellation.core.api.traits.spi.ClassTraitProvider

/** Registered via META-INF/services in test resources — proves ServiceLoader discovery. */
class TestFixtureProvider : ClassTraitProvider {
    override val id = TraitId("test.fixture.marker")
    override val displayName = "Fixture marker"

    override fun traitFor(ctx: ClassContext): ClassTrait? =
        if (ctx.isInterface) Marker else null

    object Marker : ClassTrait {
        override val id = TraitId("test.fixture.marker")
    }
}
