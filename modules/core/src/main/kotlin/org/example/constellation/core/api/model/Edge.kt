package org.example.constellation.core.api.model

data class Edge(
    val from: String,
    val to: String,
    val type: Relation,
    /** Called methods; present on [Relation.CALLS] edges only. */
    val methods: List<MethodSignature> = emptyList(),
)
