package org.example.constellation.core.api.query

import org.example.constellation.core.api.model.Edge

/** An edge filter the consumer composes. */
fun interface EdgePredicate {
    fun test(edge: Edge): Boolean
}

infix fun EdgePredicate.and(other: EdgePredicate): EdgePredicate =
    EdgePredicate { edge -> test(edge) && other.test(edge) }

infix fun EdgePredicate.or(other: EdgePredicate): EdgePredicate =
    EdgePredicate { edge -> test(edge) || other.test(edge) }

operator fun EdgePredicate.not(): EdgePredicate = EdgePredicate { edge -> !test(edge) }
