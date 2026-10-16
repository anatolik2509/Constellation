package org.example.constellation.core.properties

import org.example.constellation.core.properties.builtin.DataModelProvider
import org.example.constellation.core.properties.builtin.StaticComponentProvider
import java.util.ServiceLoader

/**
 * Open registration API. Built-ins are always present; [discover] additionally
 * loads providers registered via `META-INF/services/...ClassPropertyProvider`.
 */
class PropertyRegistry private constructor() {

    private val providers = LinkedHashMap<PropertyId, ClassPropertyProvider>()

    fun register(provider: ClassPropertyProvider) {
        providers[provider.id] = provider
    }

    fun all(): List<ClassPropertyProvider> = providers.values.toList()

    fun detectAll(ctx: ClassContext): List<ClassProperty> =
        providers.values
            .mapNotNull { it.propertiesFor(ctx) }
            .sortedBy { it.id.value }

    companion object {
        fun defaults(): PropertyRegistry = PropertyRegistry().apply {
            register(DataModelProvider())
            register(StaticComponentProvider())
        }

        /** Built-ins plus every provider discoverable via [ServiceLoader]. */
        fun discover(): PropertyRegistry = defaults().apply {
            runCatching { ServiceLoader.load(ClassPropertyProvider::class.java) }
                .getOrNull()
                ?.forEach { register(it) }
        }
    }
}
