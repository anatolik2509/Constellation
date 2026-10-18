package org.example.constellation.webui.server

import org.example.constellation.core.api.model.MethodInfo
import org.example.constellation.core.api.query.EdgePredicate
import org.example.constellation.core.api.query.NodePredicate

/** Parsed query: node/edge predicates plus the call-record filter (xcalltraits). */
data class GraphFilters(
    val nodes: NodePredicate,
    val edges: EdgePredicate,
    val calls: (MethodInfo) -> Boolean = { true },
)
