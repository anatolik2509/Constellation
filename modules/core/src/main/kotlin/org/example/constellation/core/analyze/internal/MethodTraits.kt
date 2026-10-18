package org.example.constellation.core.analyze.internal

import org.example.constellation.core.api.model.MethodTrait
import org.example.constellation.core.api.model.PrimitiveKind
import org.example.constellation.core.api.model.TypeRef

/**
 * Syntactic method classification, computed next to [DescriptorParser] from
 * name, descriptor and (for declarations) the static access flag. Purely
 * shape-based: `getX()`/`isX()` with zero params and a non-void return is a
 * getter, `setX(v)` with one param and void return is a setter — regardless
 * of whether a matching field exists (that stricter semantics belongs to
 * the DataModel provider).
 */
internal object MethodTraits {

    fun ofDeclared(name: String, descriptor: String, isStatic: Boolean): Set<MethodTrait> {
        val traits = sortedSetOf<MethodTrait>()
        traits += shape(name, descriptor)
        if (isStatic) traits += MethodTrait.STATIC
        return traits
    }

    /** Call records never carry [MethodTrait.STATIC]: static dispatch is the CALLS_STATIC edge. */
    fun ofCall(name: String, descriptor: String): Set<MethodTrait> = shape(name, descriptor)

    private fun shape(name: String, descriptor: String): Set<MethodTrait> {
        val traits = sortedSetOf<MethodTrait>()
        if (name == "<init>") {
            traits += MethodTrait.CONSTRUCTOR
            return traits
        }
        val sig = DescriptorParser.parseMethod(name, descriptor)
        val voidReturn = sig.returnType == TypeRef.Primitive(PrimitiveKind.VOID)
        val boolReturn = sig.returnType == TypeRef.Primitive(PrimitiveKind.BOOLEAN)
        val isGetter = (
            (upperTail(name, "is") != null && boolReturn) ||
                (upperTail(name, "get") != null && !voidReturn)
            ) && sig.params.isEmpty()
        val isSetter = upperTail(name, "set") != null && sig.params.size == 1 && voidReturn
        if (isGetter) traits += MethodTrait.GETTER
        if (isSetter) traits += MethodTrait.SETTER
        return traits
    }

    /** The name minus a JavaBeans prefix; null unless the tail starts uppercase. */
    private fun upperTail(name: String, prefix: String): String? {
        if (!name.startsWith(prefix)) return null
        val tail = name.removePrefix(prefix)
        return tail.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
    }
}
