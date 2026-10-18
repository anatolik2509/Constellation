package org.example.constellation.core.traits.builtin

import org.example.constellation.core.api.traits.spi.ClassContext
import org.example.constellation.core.api.traits.ClassTrait
import org.example.constellation.core.api.traits.spi.ClassTraitProvider
import org.example.constellation.core.api.traits.DataModel
import org.example.constellation.core.api.traits.spi.MethodDecl
import org.example.constellation.core.api.traits.TraitId

/**
 * Built-in [DataModel] detection: the class is a value carrier rather than behavior.
 * Carrier priority: ENUM > JAVA_RECORD > KOTLIN_DATA_CLASS > JAVA_BEAN.
 */
internal class DataModelProvider : ClassTraitProvider {

    override val id: TraitId = DataModel.ID
    override val displayName: String = "Data model"

    override fun traitFor(ctx: ClassContext): ClassTrait? {
        if (ctx.isInterface || ctx.isAnnotation || ctx.isSynthetic) return null

        if (ctx.isEnum) return DataModel(DataModel.CarrierKind.ENUM, ctx.instanceFields)

        if (ctx.recordComponents.isNotEmpty()) {
            return DataModel(DataModel.CarrierKind.JAVA_RECORD, ctx.recordComponents)
        }

        val components = ctx.instanceFields.toSortedSet()
        if (components.isEmpty()) return null

        // Kotlin data classes always generate BOTH copy() and componentN(); requiring
        // both keeps plain Java beans with a copy() method out of the data-class bucket.
        val isKotlinDataClass = ctx.methods.any { it.name == "copy" } && ctx.methods.any { isComponentN(it.name) }
        val carrier =
            if (isKotlinDataClass) DataModel.CarrierKind.KOTLIN_DATA_CLASS
            else DataModel.CarrierKind.JAVA_BEAN
        return if (isBeanShaped(ctx, components)) DataModel(carrier, components.toList()) else null
    }

    /**
     * Bean shape: every method is a constructor, an Object override, a field
     * accessor (get/is/set), a Kotlin data-class member, or a record accessor.
     */
    private fun isBeanShaped(ctx: ClassContext, fields: Set<String>): Boolean =
        ctx.methods.all { isAllowed(ctx, it, fields) }

    private fun isAllowed(ctx: ClassContext, m: MethodDecl, fields: Set<String>): Boolean {
        // Bridge/synthetic methods (e.g. compareTo(Object) erasure) do not disqualify.
        if (m.isSynthetic) return true
        if (m.name == "<init>" || m.name == "<clinit>") return true
        if (m.isStatic) return false

        return when (m.name) {
            "equals" -> m.descriptor == "(Ljava/lang/Object;)Z"
            "hashCode" -> m.descriptor == "()I"
            "toString" -> m.descriptor == "()Ljava/lang/String;"
            "copy" -> true // Kotlin data class
            else ->
                isComponentN(m.name) ||
                    // Record accessors are named after their components
                    (m.name in ctx.recordComponents && paramCount(m.descriptor) == 0) ||
                    isGetter(m, fields) ||
                    isSetter(m, fields)
        }
    }

    private fun isComponentN(name: String): Boolean {
        if (!name.startsWith("component")) return false
        val digits = name.removePrefix("component")
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    /** `getX()`/`isX()` with zero params and non-void return, matching a field. */
    private fun isGetter(m: MethodDecl, fields: Set<String>): Boolean {
        val prop = propertyName(m.name, "get", "is") ?: return false
        if (paramCount(m.descriptor) != 0) return false
        if (returnTypeIsVoid(m.descriptor)) return false
        return prop in fields ||
            "is" + prop.replaceFirstChar { it.uppercase() } in fields ||
            prop.replaceFirstChar { it.uppercase() } in fields
    }

    /** `setX(v)` with exactly one param and void return, matching a field. */
    private fun isSetter(m: MethodDecl, fields: Set<String>): Boolean {
        val prop = propertyName(m.name, "set") ?: return false
        if (paramCount(m.descriptor) != 1) return false
        if (!returnTypeIsVoid(m.descriptor)) return false
        return prop in fields || prop.replaceFirstChar { it.uppercase() } in fields
    }

    /** Strips [prefixes], applies JavaBeans decapitalization; null if the name is just the prefix. */
    private fun propertyName(name: String, vararg prefixes: String): String? {
        for (p in prefixes) {
            if (!name.startsWith(p)) continue
            val rest = name.removePrefix(p)
            if (rest.isEmpty()) return null
            return if (rest.length > 1 && rest[0].isUpperCase() && rest[1].isUpperCase()) rest
            else rest.replaceFirstChar { it.lowercase() }
        }
        return null
    }

    private fun returnTypeIsVoid(descriptor: String): Boolean =
        descriptor.substringAfter(')', "").startsWith("V")

    private fun paramCount(descriptor: String): Int {
        val params = descriptor.substringAfter('(', "").substringBefore(')', "")
        var count = 0
        var i = 0
        while (i < params.length) {
            when (params[i]) {
                // L…; is one parameter — skip to its end AND count it.
                'L' -> {
                    i = params.indexOf(';', i).takeIf { it >= 0 } ?: return count
                    count++
                }
                '[' -> {} // array prefixes: the element type below counts once
                else -> count++
            }
            i++
        }
        return count
    }
}
