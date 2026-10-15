package org.example.constellation.core.query

import org.example.constellation.core.model.Node

/** A node filter the consumer composes; core ships factories and combinators. */
fun interface NodePredicate {
    fun test(node: Node): Boolean
}

infix fun NodePredicate.and(other: NodePredicate): NodePredicate =
    NodePredicate { node -> test(node) && other.test(node) }

infix fun NodePredicate.or(other: NodePredicate): NodePredicate =
    NodePredicate { node -> test(node) || other.test(node) }

operator fun NodePredicate.not(): NodePredicate = NodePredicate { node -> !test(node) }
