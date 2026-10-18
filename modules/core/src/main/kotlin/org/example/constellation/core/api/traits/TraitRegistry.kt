package org.example.constellation.core.api.traits

import org.example.constellation.core.traits.builtin.DataModelProvider
import org.example.constellation.core.traits.builtin.StaticComponentProvider
import org.example.constellation.core.api.traits.spi.ClassContext
import org.example.constellation.core.api.traits.spi.ClassTraitProvider
import java.util.ServiceLoader

/**
 * Open registration API. Built-ins are always present; [discover] additionally
 * loads providers registered via `META-INF/services/...ClassTraitProvider`.
 */
class TraitRegistry private constructor() {

    private val providers = LinkedHashMap<TraitId, ClassTraitProvider>()

    fun register(provider: ClassTraitProvider) {
        providers[provider.id] = provider
    }

    fun all(): List<ClassTraitProvider> = providers.values.toList()

    fun detectAll(ctx: ClassContext): List<ClassTrait> =
        providers.values
            .mapNotNull { it.traitFor(ctx) }
            .sortedBy { it.id.value }

    companion object {
        fun defaults(): TraitRegistry = TraitRegistry().apply {
            register(DataModelProvider())
            register(StaticComponentProvider())
        }

        /** Built-ins plus every provider discoverable via [ServiceLoader]. */
        fun discover(): TraitRegistry = defaults().apply {
            runCatching { ServiceLoader.load(ClassTraitProvider::class.java) }
                .getOrNull()
                ?.forEach { register(it) }
        }
    }
}
