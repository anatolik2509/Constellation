package org.example.constellation.core.traits.builtin

import org.example.constellation.core.api.traits.spi.ClassContext
import org.example.constellation.core.api.traits.ClassTrait
import org.example.constellation.core.api.traits.spi.ClassTraitProvider
import org.example.constellation.core.api.traits.StaticComponent
import org.example.constellation.core.api.traits.TraitId

/**
 * Built-in [StaticComponent] detection. Carriers, by what the bytecode shows:
 * OBJECT — singleton with a static `INSTANCE` field OF ITS OWN TYPE (Kotlin `object`,
 * enum-style singleton classes);
 * STATIC_NESTED — declared static in its own InnerClasses entry (Java static
 * nested class; records are implicitly static but excluded as data carriers);
 * STATIC_HOLDER — nothing of its own but static members and it inherits nothing
 * either (Kotlin file facades, utility and constants-only classes).
 */
internal class StaticComponentProvider : ClassTraitProvider {

    override val id: TraitId = StaticComponent.ID
    override val displayName: String = "Static component"

    override fun traitFor(ctx: ClassContext): ClassTrait? {
        // Enums/records are data carriers (DataModel), not static components — their
        // static members would otherwise trip the heuristics below.
        if (ctx.isInterface || ctx.isAnnotation || ctx.isEnum || ctx.isRecord || ctx.isSynthetic) return null

        // A field merely NAMED INSTANCE can hold someone else's singleton —
        // require the field's type to be this very class.
        if (ctx.staticFields.any { it.name == "INSTANCE" && it.descriptor == "L" + ctx.name + ";" }) {
            return StaticComponent(StaticComponent.CarrierKind.OBJECT)
        }
        if (ctx.isStaticNested) {
            return StaticComponent(StaticComponent.CarrierKind.STATIC_NESTED)
        }

        // Static holder: every own member is static (or a constructor) and there is
        // own static content. Only direct Object subclasses qualify: a subclass of a
        // stateful class (say, an exception with a ctor + static factory) has state —
        // just not its own — and is a regular participant.
        val members = ctx.methods.filter { !it.isSynthetic }
        val hasStaticContent = ctx.staticFields.isNotEmpty() || members.any { it.isStatic && it.name != "<clinit>" }
        val allStatic = members.all { it.isStatic || it.name == "<init>" || it.name == "<clinit>" }
        if (ctx.superName == "java/lang/Object" && ctx.instanceFields.isEmpty() && hasStaticContent && allStatic) {
            return StaticComponent(StaticComponent.CarrierKind.STATIC_HOLDER)
        }
        return null
    }
}
