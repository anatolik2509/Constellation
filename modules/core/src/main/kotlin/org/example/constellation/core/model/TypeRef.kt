package org.example.constellation.core.model

enum class PrimitiveKind { BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, VOID }

/** A type reference used in method signatures. */
sealed interface TypeRef {
    data class Primitive(val kind: PrimitiveKind) : TypeRef

    class Array(val element: TypeRef) : TypeRef {
        override fun equals(other: Any?) = other is Array && other.element == element
        override fun hashCode() = element.hashCode() * 31
        override fun toString() = "Array($element)"
    }

    /** A class type by dot-separated name; nested classes keep `$` (`com.foo.Bar$Baz`). */
    data class Class(val fqn: String) : TypeRef

    /** Short display form: `String`, `int[]` (packages dropped). */
    fun render(): String = when (this) {
        is Primitive -> kind.name.lowercase()
        is Array -> "${element.render()}[]"
        is Class -> fqn.substringAfterLast('/').substringAfterLast('.')
    }

    /** Identity form: fully-qualified, so overloads differing only by package stay distinct. */
    fun renderFull(): String = when (this) {
        is Primitive -> kind.name.lowercase()
        is Array -> "${element.renderFull()}[]"
        is Class -> fqn
    }
}
