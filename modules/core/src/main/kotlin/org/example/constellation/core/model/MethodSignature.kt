package org.example.constellation.core.model

/** A method with a readable signature — `zip(String, int[]): int` via [render]. */
data class MethodSignature(
    val name: String,
    val params: List<TypeRef>,
    val returnType: TypeRef,
) {
    fun render(): String =
        "$name(${params.joinToString(", ") { it.render() }}): ${returnType.render()}"

    /** Identity rendering (fully-qualified types) — distinguishes same-name overloads. */
    fun renderFull(): String =
        "$name(${params.joinToString(",") { it.renderFull() }}):${returnType.renderFull()}"
}
