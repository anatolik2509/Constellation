package org.example.constellation.webui.server

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.GraphStats
import org.example.constellation.core.model.MethodRef
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind
import org.example.constellation.core.model.ParseError
import org.example.constellation.core.filter.FilterSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQueryTest {

    @Test
    fun `null query means no restrictions`() {
        assertEquals(FilterSpec(), GraphQuery.parse(null))
        assertEquals(FilterSpec(), GraphQuery.parse(""))
    }

    @Test
    fun `parses all axes`() {
        val spec = GraphQuery.parse("kinds=CLASS,POJO&edges=CALL&pkgs=com.foo,org.bar")
        assertEquals(setOf(NodeCategory.CLASS, NodeCategory.POJO), spec.kinds)
        assertEquals(setOf(EdgeType.CALL), spec.edgeTypes)
        assertEquals(listOf("com.foo", "org.bar"), spec.packages)
    }

    @Test
    fun `parses excluded packages`() {
        val spec = GraphQuery.parse("xpkgs=java.*,kotlin.*")
        assertEquals(listOf("java.*", "kotlin.*"), spec.excludedPackages)
        // absent and empty both mean "no exclusions"
        assertEquals(emptyList<String>(), GraphQuery.parse("kinds=CLASS").excludedPackages)
        assertEquals(emptyList<String>(), GraphQuery.parse("xpkgs=").excludedPackages)
    }

    @Test
    fun `unknown axis is ignored, empty values mean all`() {
        val spec = GraphQuery.parse("kinds=&edges=CALL&pkgs=&other=1")
        assertEquals(NodeCategory.entries.toSet(), spec.kinds)
        assertEquals(emptyList<String>(), spec.packages)
        assertEquals(setOf(EdgeType.CALL), spec.edgeTypes)
    }

    @Test
    fun `unknown enum token throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            GraphQuery.parse("kinds=CLASS,NOPE")
        }
    }
}

class GraphJsonTest {

    private fun tinyGraph(): CodeGraph {
        val nodes = listOf(
            Node("demo.Foo", NodeKind.CLASS, pkg = "demo", simpleName = "Foo", sourceFile = "Foo.java",
                 declaredMethods = listOf(MethodRef("run", "()V"))),
            Node("demo.IFace", NodeKind.INTERFACE, pkg = "demo", simpleName = "IFace"),
            Node("java.lang.String", NodeKind.EXTERNAL, pkg = "java.lang", simpleName = "String"),
        )
        val edges = listOf(
            Edge("demo.Foo", "demo.IFace", EdgeType.IMPLEMENTS),
            Edge("demo.Foo", "java.lang.String", EdgeType.CALL,
                 methods = listOf(MethodRef("valueOf", "(I)Ljava/lang/String;"))),
        )
        return CodeGraph(
            nodes, edges,
            GraphStats(
                2, 0, 0,
                parseErrors = listOf(ParseError("/tmp/x!/Broken.class", "bad constant pool")),
                sources = listOf("/tmp/x"),
            ),
        )
    }

    @Test
    fun `graph json shape`() {
        val json = GraphJson.graph(tinyGraph())
        assertTrue(json.startsWith("{\"nodes\":["))
        assertTrue(json.contains("\"id\":\"demo.Foo\""))
        assertTrue(json.contains("\"category\":\"CLASS\""))
        assertTrue(json.contains("\"display\":\"run(): void\""))
        assertTrue(json.contains("\"type\":\"IMPLEMENTS\""))
        assertTrue(json.contains("\"display\":\"valueOf(int): String\""))
        assertTrue(json.contains("\"sourceFile\":\"Foo.java\""))
    }

    @Test
    fun `meta json shape`() {
        val json = GraphJson.meta(tinyGraph())
        assertTrue(json.contains("\"analyzedClasses\":2"))
        assertTrue(json.contains("\"CALL\":1"))
        // external package java.lang must not be offered in the package list
        assertTrue(json.contains("\"name\":\"demo\""))
        // parse errors carry their origin
        assertTrue(json.contains("\"origin\":\"/tmp/x!/Broken.class\""))
        assertTrue(json.contains("\"message\":\"bad constant pool\""))
    }

    @Test
    fun `graph json is valid json and escapes special characters`() {
        val weird = Node(
            "demo.Quote\"Back\\slash", NodeKind.CLASS,
            pkg = "demo", simpleName = "Quote\"Back\\slash", sourceFile = "Класс.kt",
        )
        val json = GraphJson.graph(CodeGraph(listOf(weird), emptyList(), tinyGraph().stats))
        // Jackson produces valid JSON; parse it back and check round-trip
        val parsed = com.fasterxml.jackson.databind.ObjectMapper().readTree(json)
        assertEquals("demo.Quote\"Back\\slash", parsed["nodes"][0]["id"].asText())
        assertEquals("Класс.kt", parsed["nodes"][0]["sourceFile"].asText())
    }
}
