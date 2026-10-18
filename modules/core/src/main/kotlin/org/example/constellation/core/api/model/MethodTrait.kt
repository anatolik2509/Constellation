package org.example.constellation.core.api.model

/**
 * Syntactic traits of a method, computed by the analyzer from name, descriptor
 * and access flags. Declared methods may carry any of these; call records
 * never carry [STATIC] — static dispatch is the [Relation.CALLS_STATIC] edge.
 */
enum class MethodTrait { STATIC, CONSTRUCTOR, GETTER, SETTER }
