package org.example.constellation.core.query

import org.example.constellation.core.model.ClassKind
import org.example.constellation.core.model.ClassOrigin
import org.example.constellation.core.model.Node
import org.example.constellation.core.traits.TraitId

/** Ready-made node predicates; combine with `and`/`or`/`not`. */
object NodePredicates {

    fun all(): NodePredicate = NodePredicate { true }

    fun none(): NodePredicate = NodePredicate { false }

    fun kind(vararg kinds: ClassKind): NodePredicate = NodePredicate { it.kind in kinds }

    fun external(): NodePredicate = NodePredicate { it.isExternal }

    fun sourceSet(name: String): NodePredicate = NodePredicate {
        val origin = it.origin
        origin is ClassOrigin.Project && origin.sourceSet == name
    }

    fun module(vararg names: String): NodePredicate = NodePredicate {
        val origin = it.origin
        origin is ClassOrigin.Project && origin.module in names
    }

    fun hasTrait(id: TraitId): NodePredicate = NodePredicate { node ->
        node.traits.any { it.id == id }
    }

    fun hasAnyTrait(ids: Collection<TraitId>): NodePredicate = NodePredicate { node ->
        ids.any { id -> node.traits.any { it.id == id } }
    }

    fun inPackage(vararg patterns: String): NodePredicate = NodePredicate { node ->
        patterns.any { PackageMatcher.matches(it, node.pkg) }
    }

    fun notInPackage(vararg patterns: String): NodePredicate = NodePredicate { node ->
        patterns.none { PackageMatcher.matches(it, node.pkg) }
    }
}
