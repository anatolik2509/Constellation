package org.example.constellation.core.properties

/** Registered via META-INF/services in test resources — proves ServiceLoader discovery. */
class TestFixtureProvider : ClassPropertyProvider {
    override val id = PropertyId("test.fixture.marker")
    override val displayName = "Fixture marker"

    override fun propertiesFor(ctx: ClassContext): ClassProperty? =
        if (ctx.isInterface) Marker else null

    object Marker : ClassProperty {
        override val id = PropertyId("test.fixture.marker")
    }
}
