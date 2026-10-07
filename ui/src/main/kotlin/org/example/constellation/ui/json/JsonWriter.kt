package org.example.constellation.ui.json

/**
 * Minimal JSON writer. The graph payload is plain structural data, so a hand-rolled
 * emitter avoids pulling in a serialization framework.
 *
 * Output is pure ASCII: control chars and non-ASCII characters are `\uXXXX`-escaped.
 */
class JsonWriter {

    private val sb = StringBuilder()

    /** One entry per open container; `true` once the container holds an element. */
    private val needsComma = ArrayDeque<Boolean>()

    /** Set after [key] so the following value does not get a comma prefix. */
    private var suppressNext = false

    fun obj(block: JsonWriter.() -> Unit): JsonWriter {
        elementPrefix()
        sb.append('{')
        needsComma.addLast(false)
        block()
        sb.append('}')
        needsComma.removeLast()
        return this
    }

    fun arr(block: JsonWriter.() -> Unit): JsonWriter {
        elementPrefix()
        sb.append('[')
        needsComma.addLast(false)
        block()
        sb.append(']')
        needsComma.removeLast()
        return this
    }

    /** Writes `"name":` and suppresses the separator before the value. */
    fun key(name: String): JsonWriter {
        elementPrefix()
        string(name)
        sb.append(':')
        suppressNext = true
        return this
    }

    fun str(value: String): JsonWriter {
        elementPrefix()
        string(value)
        return this
    }

    fun int(value: Int): JsonWriter {
        elementPrefix()
        sb.append(value)
        return this
    }

    fun bool(value: Boolean): JsonWriter {
        elementPrefix()
        sb.append(value)
        return this
    }

    override fun toString(): String = sb.toString()

    private fun elementPrefix() {
        if (suppressNext) {
            suppressNext = false
            return
        }
        if (needsComma.isNotEmpty()) {
            if (needsComma.last()) sb.append(',')
            needsComma.removeLast()
            needsComma.addLast(true)
        }
    }

    private fun string(s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else ->
                    if (ch.code < ' '.code || ch.code > 126) {
                        sb.append("\\u").append(String.format("%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
            }
        }
        sb.append('"')
    }
}
