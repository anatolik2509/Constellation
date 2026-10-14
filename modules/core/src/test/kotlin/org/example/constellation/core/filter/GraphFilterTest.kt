package org.example.constellation.core.filter

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphFilterTest {

    private fun graph(): CodeGraph {
        fun node(id: String, kind: NodeKind, pojo: Boolean = false, pkg: String = "") =
            Node(id = id, kind = kind, pojo = pojo, pkg = pkg, simpleName = id.substringAfterLast('.'))

        val nodes = listOf(
            node("app.Service", NodeKind.CLASS, pkg = "app"),
            node("app.Dto", NodeKind.CLASS, pojo = true, pkg = "app"),
            node("app.Api", NodeKind.INTERFACE, pkg = "app"),
            node("app.Marker", NodeKind.ANNOTATION, pkg = "app"),
            node("lib.Util", NodeKind.EXTERNAL, pkg = "lib"),
        )
        val edges = listOf(
            Edge("app.Service", "app.Dto", EdgeType.CALL),
            Edge("app.Service", "app.Api", EdgeType.IMPLEMENTS),
            Edge("app.Service", "lib.Util", EdgeType.CALL),
            Edge("app.Dto", "app.Api", EdgeType.INHERITANCE),
        )
        return CodeGraph(nodes, edges, org.example.constellation.core.model.GraphStats(4, 0, 0, emptyList(), emptyList()))
    }

    @Test
    fun `default spec keeps everything`() {
        val out = GraphFilter.apply(graph(), FilterSpec())
        assertEquals(5, out.nodes.size)
        assertEquals(4, out.edges.size)
    }

    @Test
    fun `pojo checkbox hides pojos but keeps plain classes`() {
        val out = GraphFilter.apply(
            graph(),
            FilterSpec(
                kinds = NodeCategory.entries.filter { it != NodeCategory.POJO }.toSet(),
                edgeTypes = EdgeType.entries.toSet(),
            ),
        )
        assertFalse(out.nodes.any { it.category == NodeCategory.POJO })
        assertTrue(out.nodes.any { it.id == "app.Service" })
        // Dto gone -> its inheritance edge gone; Service->Dto call gone
        assertFalse(out.edges.any { it.from == "app.Dto" || it.to == "app.Dto" })
    }

    @Test
    fun `edge type filter removes only that type and prunes orphaned externals`() {
        val out = GraphFilter.apply(
            graph(),
            FilterSpec(
                kinds = NodeCategory.entries.toSet(),
                edgeTypes = EdgeType.entries.filter { it != EdgeType.CALL }.toSet(),
            ),
        )
        assertTrue(out.edges.none { it.type == EdgeType.CALL })
        // lib.Util survives only through CALL edges -> pruned
        assertFalse(out.nodes.any { it.id == "lib.Util" })
        assertTrue(out.nodes.any { it.id == "app.Service" })
    }

    @Test
    fun `package filter by exact package`() {
        val out = GraphFilter.apply(
            graph(),
            FilterSpec(packages = listOf("app")),
        )
        assertEquals(listOf("app.Api", "app.Dto", "app.Marker", "app.Service"), out.nodes.map { it.id }.sorted())
        assertTrue(out.edges.none { it.to == "lib.Util" })
    }

    @Test
    fun `package patterns`() {
        assertTrue(PackageMatcher.matches("com.foo", "com.foo"))
        assertTrue(PackageMatcher.matches("com.foo", "com.foo.sub.deep"))
        assertTrue(PackageMatcher.matches("com.foo.*", "com.foo"))
        assertTrue(PackageMatcher.matches("com.foo.*", "com.foo.sub"))
        assertFalse(PackageMatcher.matches("com.foo", "com.foobar"))
        assertFalse(PackageMatcher.matches("com.foo", "com"))
        assertTrue(PackageMatcher.matches("com.f*", "com.foo"))
        // `*` does not cross dots within a segment, but matched packages still include subpackages
        assertTrue(PackageMatcher.matches("com.f*", "com.foo.sub"))
        assertTrue(PackageMatcher.matches("", "")) // default package
        assertTrue(PackageMatcher.matches("*", "com"))
    }

    @Test
    fun `package filter applies to externals too`() {
        // lib.Util is external with pkg "lib": unselected package -> node and its edge are gone
        val out = GraphFilter.apply(
            graph(),
            FilterSpec(packages = listOf("app")),
        )
        assertFalse(out.nodes.any { it.id == "lib.Util" })
        assertTrue(out.edges.none { it.to == "lib.Util" })

        // ...and an external survives when its package is selected
        val out2 = GraphFilter.apply(graph(), FilterSpec(packages = listOf("app", "lib")))
        assertTrue(out2.nodes.any { it.id == "lib.Util" })
        assertTrue(out2.edges.any { it.to == "lib.Util" })
    }

    @Test
    fun `excluded packages drop matching nodes whatever else is selected`() {
        val out = GraphFilter.apply(
            graph(),
            FilterSpec(excludedPackages = listOf("lib", "java.*")),
        )
        assertFalse(out.nodes.any { it.id == "lib.Util" })
        assertTrue(out.edges.none { it.to == "lib.Util" })
        // non-matching nodes stay
        assertTrue(out.nodes.any { it.id == "app.Service" })

        // exclusion wins over inclusion of the same package
        val both = GraphFilter.apply(
            graph(),
            FilterSpec(packages = listOf("lib"), excludedPackages = listOf("lib")),
        )
        assertTrue(both.nodes.none { it.id == "lib.Util" })
    }
}
