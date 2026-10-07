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

    /** Splits the parameter part of a descriptor into single type descriptors. */
    private fun parseTypes(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '[') {
                var j = i
                while (j < s.length && s[j] == '[') j++
                val end = if (j < s.length && s[j] == 'L') s.indexOf(';', j) else j
                if (end < 0 || end + 1 > s.length) break
                out.add(s.substring(i, end + 1))
                i = end + 1
            } else if (c == 'L') {
                val end = s.indexOf(';', i)
                if (end < 0) break
                out.add(s.substring(i, end + 1))
                i = end + 1
            } else {
                out.add(s.substring(i, i + 1))
                i++
            }
        }
        return out
    }
}
