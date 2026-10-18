package org.example.constellation.webui.server

import org.example.constellation.core.api.model.ClassKind
import org.example.constellation.core.api.model.ClassOrigin
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.Relation
import org.example.constellation.core.api.traits.DataModel
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQueryTest {

    @Test
    fun `null query means no restrictions`() {
        val (nodes, edges) = GraphQuery.parse(null)
        assertTrue(nodes.test(anyNode()))
        assertTrue(edges.test(Edge("a", "b", Relation.CALLS)))
    }

    @Test
    fun `parses categories, relations and packages`() {
        val (nodes, edges) = GraphQuery.parse("kinds=CLASS,DATA&edges=CALLS&pkgs=com.foo&xpkgs=java.*")

        val projectClass = node(
            "com.foo.A", origin = ClassOrigin.Project("m", "main"),
            traits = listOf(DataModel(DataModel.CarrierKind.JAVA_BEAN, listOf("x"))),
        )
        val external = node("java.lang.String", origin = ClassOrigin.External)
        assertTrue(nodes.test(projectClass)) // DATA
        assertTrue(!nodes.test(external)) // xpkgs excludes java.*
        assertTrue(edges.test(Edge("a", "b", Relation.CALLS)))
        assertTrue(!edges.test(Edge("a", "b", Relation.EXTENDS)))

        val plain = node("com.foo.Plain", origin = ClassOrigin.Project("m", "main"))
        assertTrue(nodes.test(plain)) // CLASS
        val iface = node("com.foo.I", ClassKind.INTERFACE, origin = ClassOrigin.Project("m", "main"))
        assertTrue(!nodes.test(iface)) // not selected
    }

    @Test
    fun `edges parameter accepts CALLS_STATIC`() {
        val (_, edges) = GraphQuery.parse("edges=CALLS_STATIC")
        assertTrue(edges.test(Edge("a", "b", Relation.CALLS_STATIC)))
        assertTrue(!edges.test(Edge("a", "b", Relation.CALLS)))
        assertTrue(!edges.test(Edge("a", "b", Relation.IMPLEMENTS)))
    }

    @Test
    fun `test category selects the test source set`() {
        val (nodes, _) = GraphQuery.parse("kinds=TEST")
        val test = node("app.FooTest", origin = ClassOrigin.Project("app", "test"))
        val main = node("app.Foo", origin = ClassOrigin.Project("app", "main"))
        assertTrue(nodes.test(test))
        assertTrue(!nodes.test(main))
    }

    @Test
    fun `categories are mutually exclusive`() {
        // a plain class in the test source set is TEST, not CLASS
        val (withClass, _) = GraphQuery.parse("kinds=CLASS")
        val test = node("app.FooTest", origin = ClassOrigin.Project("app", "test"))
        assertTrue(!withClass.test(test))
    }

    @Test
    fun `default package round-trips through the sentinel`() {
        val (nodes, _) = GraphQuery.parse("pkgs=(default),com.foo")
        val defaultPkg = node("Default", origin = ClassOrigin.Project("m", "main")) // pkg == ""
        val foo = node("com.foo.A", origin = ClassOrigin.Project("m", "main"))
        val bar = node("com.bar.B", origin = ClassOrigin.Project("m", "main"))
        assertTrue(nodes.test(defaultPkg))
        assertTrue(nodes.test(foo))
        assertTrue(!nodes.test(bar))
    }

    @Test
    fun `xtraits hides carriers of the listed traits`() {
        val (nodes, _) = GraphQuery.parse("xtraits=constellation.static-component")
        val plain = node("app.Service", origin = ClassOrigin.Project("app", "main"))
        val stat = node(
            "app.Registry", origin = ClassOrigin.Project("app", "main"),
            traits = listOf(
                org.example.constellation.core.api.traits.StaticComponent(
                    org.example.constellation.core.api.traits.StaticComponent.CarrierKind.OBJECT,
                ),
            ),
        )
        assertTrue(nodes.test(plain))
        assertTrue(!nodes.test(stat))
    }

    @Test
    fun `unknown token throws`() {
        assertThrows(IllegalArgumentException::class.java) { GraphQuery.parse("kinds=CLASS,NOPE") }
        assertThrows(IllegalArgumentException::class.java) { GraphQuery.parse("edges=NOPE") }
    }

    private fun node(
        id: String,
        kind: ClassKind = ClassKind.CLASS,
        origin: ClassOrigin,
        traits: List<org.example.constellation.core.api.traits.ClassTrait> = emptyList(),
    ) = Node(
        id = id, kind = kind, origin = origin, pkg = id.substringBeforeLast('.', ""),
        simpleName = id.substringAfterLast('.'), traits = traits,
    )

    private fun anyNode() = node("x.X", origin = ClassOrigin.Project("m", "main"))
}
