package org.example.constellation.core.query

import org.example.constellation.core.model.Relation

/** Ready-made edge predicates. */
object EdgePredicates {

    fun all(): EdgePredicate = EdgePredicate { true }

    fun none(): EdgePredicate = EdgePredicate { false }

    fun types(vararg relations: Relation): EdgePredicate = EdgePredicate { it.type in relations }
}
