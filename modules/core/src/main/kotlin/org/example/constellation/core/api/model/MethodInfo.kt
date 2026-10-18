package org.example.constellation.core.api.model

/**
 * A declared method or a call record: its [signature] plus computed [traits].
 * Identity and sorting stay signature-based; traits ride along.
 */
data class MethodInfo(
    val signature: MethodSignature,
    val traits: Set<MethodTrait> = emptySet(),
) {
    fun render(): String = signature.render()
}
