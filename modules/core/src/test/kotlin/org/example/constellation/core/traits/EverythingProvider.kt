package org.example.constellation.core.properties

object EverythingProvider : ClassPropertyProvider {
    override val id = PropertyId("test.everything")
    override val displayName = "Everything"

    override fun propertiesFor(ctx: ClassContext): ClassProperty? =
        if (ctx.name.startsWith("demo/")) Marker else null

    object Marker : ClassProperty {
        override val id = PropertyId("test.everything")
    }
}
