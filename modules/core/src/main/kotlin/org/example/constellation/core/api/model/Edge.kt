package org.example.constellation.core.api.model

data class Edge(
    val from: String,
    val to: String,
    val type: Relation,
    /** Called methods; present on [Relation.CALLS] and [Relation.CALLS_STATIC] edges only. */
    val methods: List<MethodSignature> = emptyList(),
)
