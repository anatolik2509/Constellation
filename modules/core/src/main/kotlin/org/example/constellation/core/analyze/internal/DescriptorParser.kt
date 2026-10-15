package org.example.constellation.core.analyze.internal

import org.example.constellation.core.model.MethodSignature
import org.example.constellation.core.model.PrimitiveKind
import org.example.constellation.core.model.TypeRef

/**
 * Parses raw JVM method descriptors (`(Ljava/lang/String;[I)V`) into
 * [MethodSignature]s. The last place in the codebase where descriptors exist —
 * nothing parsed here may leave the analyzer as a descriptor.
 */
internal object DescriptorParser {

    private val PRIMITIVES = mapOf(
        'B' to PrimitiveKind.BYTE,
        'C' to PrimitiveKind.CHAR,
        'D' to PrimitiveKind.DOUBLE,
        'F' to PrimitiveKind.FLOAT,
        'I' to PrimitiveKind.INT,
        'J' to PrimitiveKind.LONG,
        'S' to PrimitiveKind.SHORT,
        'V' to PrimitiveKind.VOID,
        'Z' to PrimitiveKind.BOOLEAN,
    )

    fun parseMethod(name: String, descriptor: String): MethodSignature {
        val close = descriptor.lastIndexOf(')')
        if (descriptor.isEmpty() || descriptor[0] != '(' || close < 0) {
            // Malformed: fall back to a single opaque class ref so the model stays well-typed.
            val raw = TypeRef.Class(descriptor)
            return MethodSignature(name, listOf(raw), raw)
        }
        return MethodSignature(
            name = name,
            params = parseList(descriptor.substring(1, close)),
            returnType = parseStartingAt(descriptor.substring(close + 1), 0)?.first ?: TypeRef.Class("?"),
        )
    }

    private fun parseList(s: String): List<TypeRef> {
        val types = mutableListOf<TypeRef>()
        var index = 0
        while (index < s.length) {
            val parsed = parseStartingAt(s, index) ?: break
            types += parsed.first
            index = parsed.second
        }
        return types
    }

    /** One type and the index just past it; null if malformed. */
    private fun parseStartingAt(s: String, start: Int): Pair<TypeRef, Int>? {
        var elementStart = start
        while (elementStart < s.length && s[elementStart] == '[') elementStart++
        if (elementStart >= s.length) return null

        val (element, end) = when (s[elementStart]) {
            'L' -> {
                val semicolon = s.indexOf(';', elementStart)
                if (semicolon < 0) return null
                TypeRef.Class(s.substring(elementStart + 1, semicolon).replace('/', '.')) to semicolon + 1
            }
            else -> {
                val primitive = PRIMITIVES[s[elementStart]] ?: return null
                TypeRef.Primitive(primitive) to elementStart + 1
            }
        }

        var type = element
        repeat(elementStart - start) { type = TypeRef.Array(type) }
        return type to end
    }
}
