package org.example.constellation.core.util

/** Formats raw JVM descriptors into readable Java/Kotlin-ish signatures. */
object Descriptors {

    private val PRIMITIVES = mapOf(
        'B' to "byte", 'C' to "char", 'D' to "double", 'F' to "float",
        'I' to "int", 'J' to "long", 'S' to "short", 'Z' to "boolean", 'V' to "void",
    )

    /** `(Ljava/lang/String;[I)V` + `zip` -> `zip(String, int[]): void`. */
    fun formatMethod(name: String, descriptor: String): String {
        val close = descriptor.lastIndexOf(')')
        if (descriptor.isEmpty() || descriptor[0] != '(' || close < 0) {
            return "$name($descriptor)"
        }
        val params = parseTypes(descriptor.substring(1, close))
        val ret = descriptor.substring(close + 1)
        val paramsStr = params.joinToString(", ") { formatType(it) }
        return "$name($paramsStr): ${formatType(ret)}"
    }

    /** Single field/return type descriptor: `Ljava/lang/String;` -> `String`, `[I` -> `int[]`. */
    fun formatType(type: String): String {
        var depth = 0
        var i = 0
        while (i < type.length && type[i] == '[') {
            depth++
            i++
        }
        if (i >= type.length) return type
        val base = when (type[i]) {
            'L' -> type.substring(i + 1, type.length - 1).substringAfterLast('/')
            else -> PRIMITIVES[type[i]] ?: type
        }
        return base + "[]".repeat(depth)
    }

    /** One parsed type: its descriptor text and the index just past it. */
    private data class ParsedType(val descriptor: String, val endIndex: Int)

    /** Splits the parameter part of a descriptor into single type descriptors. */
    private fun parseTypes(params: String): List<String> {
        val types = mutableListOf<String>()
        var index = 0
        while (index < params.length) {
            val parsed = parseTypeStartingAt(params, index) ?: break // malformed tail
            types += parsed.descriptor
            index = parsed.endIndex
        }
        return types
    }

    /**
     * Reads one type descriptor starting at [index]:
     * `[`-prefixes (arrays) followed by either `L…;` (object) or a single primitive char.
     * Returns null if the input is malformed.
     */
    private fun parseTypeStartingAt(params: String, index: Int): ParsedType? {
        var typeStart = index
        while (typeStart < params.length && params[typeStart] == '[') typeStart++
        if (typeStart >= params.length) return null

        if (params[typeStart] == 'L') {
            val semicolon = params.indexOf(';', typeStart)
            if (semicolon < 0) return null
            return ParsedType(params.substring(index, semicolon + 1), semicolon + 1)
        }
        return ParsedType(params.substring(index, typeStart + 1), typeStart + 1)
    }
}
