package org.example.constellation.core.api.model

data class Edge(
    val from: String,
    val to: String,
    val type: Relation,
    /** Called methods with their traits; present on call edges only. */
    val methods: List<MethodInfo> = emptyList(),
)
