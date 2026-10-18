package org.example.constellation.webui.server

import org.example.constellation.core.model.ClassKind
import org.example.constellation.core.model.Node
import org.example.constellation.core.traits.DataModel
import org.example.constellation.core.query.NodePredicate
import org.example.constellation.core.query.NodePredicates
import org.example.constellation.core.query.and
import org.example.constellation.core.query.not

/**
 * The UI's category palette: display categories mapped to core predicates.
 * Order is precedence for [of]: External beats Test beats Data beats the kinds —
 * a plain class in the test source set is a Test first. New providers surface
 * generically through `traits` on nodes and the meta trait catalog.
 */
object Categories {

    data class Entry(val id: String, val predicate: () -> NodePredicate)

    val entries: List<Entry> = listOf(
        Entry("EXTERNAL") { NodePredicates.external() },
        Entry("TEST") { NodePredicates.sourceSet("test") },
        Entry("DATA") { NodePredicates.hasTrait(DataModel.ID) },
        Entry("CLASS") { NodePredicates.kind(ClassKind.CLASS) and !NodePredicates.hasTrait(DataModel.ID) },
        Entry("INTERFACE") { NodePredicates.kind(ClassKind.INTERFACE) },
        Entry("ANNOTATION") { NodePredicates.kind(ClassKind.ANNOTATION) },
    )

    fun of(node: Node): String =
        entries.firstOrNull { it.predicate().test(node) }?.id ?: "CLASS"

    /** Categories are mutually exclusive labels: a node matches iff [of] returns one of [ids]. */
    fun predicateFor(ids: Collection<String>): NodePredicate? =
        if (ids.isEmpty()) null else NodePredicate { node -> of(node) in ids }
}
