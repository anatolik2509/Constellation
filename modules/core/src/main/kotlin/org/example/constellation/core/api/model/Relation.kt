package org.example.constellation.core.api.model

/** How one class relates to another. */
enum class Relation {
    CALLS,
    /** `invokestatic` dispatch (and method references to static methods). */
    CALLS_STATIC,
    EXTENDS,
    IMPLEMENTS,
}
