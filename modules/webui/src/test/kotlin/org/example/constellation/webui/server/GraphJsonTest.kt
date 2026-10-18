package org.example.constellation.webui.server

import org.example.constellation.core.api.model.ClassKind
import org.example.constellation.core.api.model.ClassOrigin
import org.example.constellation.core.api.model.CodeGraph
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.GraphStats
import org.example.constellation.core.api.model.MethodInfo
import org.example.constellation.core.api.model.MethodSignature
import org.example.constellation.core.api.model.MethodTrait
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.PrimitiveKind
import org.example.constellation.core.api.model.Relation
import org.example.constellation.core.api.model.TypeRef
import org.example.constellation.core.api.traits.DataModel
import org.example.constellation.core.api.query.NodePredicates
import org.example.constellation.core.api.query.and
import org.example.constellation.core.api.query.filter
import org.example.constellation.core.api.query.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphJsonTest {

    private fun tinyGraph(): CodeGraph {
        val nodes = listOf(
            Node(
                "demo.Foo", ClassKind.CLASS,
                origin = ClassOrigin.Project("app", "main"),
                pkg = "demo", simpleName = "Foo", sourceFile = "Foo.java",
                declaredMethods = listOf(
                    MethodInfo(
                        MethodSignature("getName", emptyList(), TypeRef.Class("java.lang.String")),
                        setOf(MethodTrait.GETTER),
                    ),
                ),
            ),
            Node(
                "demo.Dto", ClassKind.CLASS,
                origin = ClassOrigin.Project("app", "main"),
                pkg = "demo", simpleName = "Dto",
                traits = listOf(DataModel(DataModel.CarrierKind.KOTLIN_DATA_CLASS, listOf("a", "b"))),
            ),
            Node(
                "demo.IFace", ClassKind.INTERFACE,
                origin = ClassOrigin.Project("app", "main"),
                pkg = "demo", simpleName = "IFace",
            ),
            Node(
                "java.lang.String", ClassKind.CLASS,
                origin = ClassOrigin.External,
                pkg = "java.lang", simpleName = "String",
            ),
        )
        val edges = listOf(
            Edge("demo.Foo", "demo.IFace", Relation.IMPLEMENTS),
            Edge(
                "demo.Foo", "java.lang.String", Relation.CALLS,
                methods = listOf(
                    MethodInfo(
                        MethodSignature(
                            "valueOf",
                            listOf(TypeRef.Primitive(PrimitiveKind.INT)),
                            TypeRef.Class("java.lang.String"),
                        ),
                    ),
                    MethodInfo(
                        MethodSignature("isEmpty", emptyList(), TypeRef.Primitive(PrimitiveKind.BOOLEAN)),
                        setOf(MethodTrait.GETTER),
                    ),
                ),
            ),
        )
        return CodeGraph(nodes, edges, GraphStats(3, 0, 0, emptyList(), listOf("/tmp/x")))
    }

    @Test
    fun `graph json shape`() {
        val json = GraphJson.graph(tinyGraph())
        assertTrue(json.startsWith("{\"nodes\":["))
        assertTrue(json.contains("\"id\":\"demo.Foo\""))
        assertTrue(json.contains("\"category\":\"CLASS\""))
        assertTrue(json.contains("\"category\":\"DATA\""))
        assertTrue(json.contains("\"display\":\"getName(): String\""))
        assertTrue(json.contains("\"display\":\"valueOf(int): String\""))
        assertTrue(json.contains("\"sourceFile\":\"Foo.java\""))
        assertTrue(json.contains("\"module\":\"app\""))
        assertTrue(json.contains("\"carrier\":\"KOTLIN_DATA_CLASS\""))
        // method traits ride on both declared methods and call records
        assertTrue(json.contains("\"traits\":[\"GETTER\"]"))
    }

    @Test
    fun `meta json shape`() {
        val json = GraphJson.meta(tinyGraph())
        assertTrue(json.contains("\"analyzedClasses\":3"))
        assertTrue(json.contains("\"CALLS\":1"))
        assertTrue(json.contains("\"CALLS_STATIC\":0"))
        assertTrue(json.contains("\"DATA\":1"))
        assertTrue(json.contains("\"constellation.data-model\""))
        assertTrue(json.contains("\"displayName\":\"Data model\""))
        assertTrue(json.contains("\"methodTraitCatalog\":[{\"id\":\"GETTER\",\"count\":1}]"))
        // external package java.lang must not be offered in the package list
        assertTrue(json.contains("\"name\":\"demo\""))
    }

    @Test
    fun `graph json is valid json and escapes special characters`() {
        val weird = Node(
            "demo.Quote\"Back\\slash", ClassKind.CLASS,
            origin = ClassOrigin.Project("m", "main"),
            pkg = "demo", simpleName = "Quote\"Back\\slash", sourceFile = "Класс.kt",
        )
        val json = GraphJson.graph(CodeGraph(listOf(weird), emptyList(), tinyGraph().stats))
        val parsed = com.fasterxml.jackson.databind.ObjectMapper().readTree(json)
        assertEquals("demo.Quote\"Back\\slash", parsed["nodes"][0]["id"].asText())
        assertEquals("Класс.kt", parsed["nodes"][0]["sourceFile"].asText())
    }

    @Test
    fun `categories compose through core predicates`() {
        val g = tinyGraph()
        val onlyData = g.filter(nodes = NodePredicates.hasTrait(DataModel.ID))
        assertEquals(listOf("demo.Dto"), onlyData.nodes.map { it.id })
        val noData = g.filter(
            nodes = !NodePredicates.hasTrait(DataModel.ID) and NodePredicates.kind(ClassKind.CLASS),
        )
        assertTrue(noData.nodes.none { it.id == "demo.Dto" })
    }
}
