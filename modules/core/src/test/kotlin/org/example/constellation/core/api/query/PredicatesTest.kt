package org.example.constellation.core.api.query

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
import org.example.constellation.core.api.traits.TraitId
import org.example.constellation.core.query.PackageMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PredicatesTest {

    private fun node(
        id: String,
        kind: ClassKind = ClassKind.CLASS,
        origin: ClassOrigin = ClassOrigin.Project("app", "main"),
        pkg: String = "",
        traits: List<org.example.constellation.core.api.traits.ClassTrait> = emptyList(),
    ) = Node(
        id = id, kind = kind, origin = origin, pkg = pkg,
        simpleName = id.substringAfterLast('.'), sourceFile = null,
        declaredMethods = listOf(
            MethodInfo(MethodSignature("x", listOf(TypeRef.Primitive(PrimitiveKind.INT)), TypeRef.Primitive(PrimitiveKind.VOID))),
        ),
        traits = traits,
    )

    private fun graph(): CodeGraph {
        val nodes = listOf(
            node("app.Service", pkg = "app"),
            node("app.Dto", pkg = "app", traits = listOf(DataModel(DataModel.CarrierKind.JAVA_BEAN, listOf("a")))),
            node("app.Api", ClassKind.INTERFACE, pkg = "app"),
            node("app.Marker", ClassKind.ANNOTATION, pkg = "app"),
            node("app.FooTest", origin = ClassOrigin.Project("app", "test"), pkg = "app"),
            node("lib.Util", origin = ClassOrigin.External, pkg = "lib"),
        )
        val edges = listOf(
            Edge("app.Service", "app.Dto", Relation.CALLS),
            Edge("app.Service", "app.Api", Relation.IMPLEMENTS),
            Edge("app.Service", "lib.Util", Relation.CALLS),
            Edge("app.Dto", "app.Api", Relation.EXTENDS),
        )
        return CodeGraph(nodes, edges, GraphStats(5, 0, 0, emptyList(), emptyList()))
    }

    @Test
    fun `default filter keeps everything`() {
        val out = graph().filter()
        assertEquals(6, out.nodes.size)
        assertEquals(4, out.edges.size)
    }

    @Test
    fun `kind predicate`() {
        val out = graph().filter(nodes = NodePredicates.kind(ClassKind.CLASS))
        assertTrue(out.nodes.all { it.kind == ClassKind.CLASS })

        // External classes are approximated as CLASS (they were not analyzed);
        // excluding them is an explicit predicate, not a kind fact.
        val withoutExternals = graph().filter(
            nodes = NodePredicates.kind(ClassKind.CLASS) and !NodePredicates.external(),
        )
        assertTrue(withoutExternals.nodes.none { it.isExternal })
    }

    @Test
    fun `hasTrait covers the old pojo toggle`() {
        val out = graph().filter(nodes = !NodePredicates.hasTrait(DataModel.ID))
        assertFalse(out.nodes.any { it.id == "app.Dto" })
        assertTrue(out.nodes.any { it.id == "app.Service" })
        assertFalse(out.edges.any { it.from == "app.Dto" || it.to == "app.Dto" })
    }

    @Test
    fun `sourceSet and module predicates`() {
        val tests = graph().filter(nodes = NodePredicates.sourceSet("test"))
        assertEquals(listOf("app.FooTest"), tests.nodes.map { it.id })

        val byModule = graph().filter(nodes = NodePredicates.module("lib"))
        assertTrue(byModule.nodes.isEmpty()) // external lib.Util has no module
    }

    @Test
    fun `edge type filter prunes orphaned externals`() {
        val out = graph().filter(edges = !EdgePredicates.types(Relation.CALLS))
        assertTrue(out.edges.none { it.type == Relation.CALLS })
        assertFalse(out.nodes.any { it.id == "lib.Util" }) // survived only through CALLS
        assertTrue(out.nodes.any { it.id == "app.Service" })
    }

    @Test
    fun `filterCalls trims records, drops emptied edges and orphaned externals`() {
        val getter = MethodInfo(
            MethodSignature("getX", emptyList(), TypeRef.Class("java.lang.String")),
            setOf(MethodTrait.GETTER),
        )
        val plain = MethodInfo(MethodSignature("work", emptyList(), TypeRef.Primitive(PrimitiveKind.VOID)))
        val graph = CodeGraph(
            nodes = listOf(
                node("app.A", pkg = "app"),
                node("app.B", pkg = "app"),
                node("lib.X", origin = ClassOrigin.External, pkg = "lib"),
            ),
            edges = listOf(
                Edge("app.A", "app.B", Relation.CALLS, listOf(getter, plain)),
                Edge("app.A", "lib.X", Relation.CALLS, listOf(getter)), // emptied -> dropped
                Edge("app.B", "app.A", Relation.EXTENDS), // structural, always survives
            ),
            stats = GraphStats(3, 0, 0, emptyList(), emptyList()),
        )

        val out = graph.filterCalls { MethodTrait.GETTER !in it.traits }

        val keptCall = out.edges.first { it.type == Relation.CALLS }
        assertEquals(listOf("work"), keptCall.methods.map { it.signature.name })
        assertTrue(out.edges.none { it.to == "lib.X" })
        assertTrue(out.nodes.none { it.id == "lib.X" }) // pruned by the same orphan rule
        assertTrue(out.edges.any { it.type == Relation.EXTENDS })
    }

    @Test
    fun `combinators and packages`() {
        val out = graph().filter(
            nodes = NodePredicates.inPackage("app") and !NodePredicates.hasTrait(DataModel.ID) or NodePredicates.external(),
        )
        assertTrue(out.nodes.any { it.id == "app.Service" })
        assertFalse(out.nodes.any { it.id == "app.Dto" })
        assertTrue(out.nodes.any { it.id == "lib.Util" })
    }

    @Test
    fun `package patterns`() {
        assertTrue(PackageMatcher.matches("com.foo", "com.foo"))
        assertTrue(PackageMatcher.matches("com.foo", "com.foo.sub.deep"))
        assertTrue(PackageMatcher.matches("com.foo.*", "com.foo"))
        assertFalse(PackageMatcher.matches("com.foo", "com.foobar"))
        assertFalse(PackageMatcher.matches("com.foo", "com"))
        assertTrue(PackageMatcher.matches("com.f*", "com.foo"))
        assertTrue(PackageMatcher.matches("", "")) // default package
    }

    @Test
    fun `predicate on unknown trait id matches nothing`() {
        val out = graph().filter(nodes = NodePredicates.hasTrait(TraitId("nope.nope")))
        assertTrue(out.nodes.isEmpty())
    }
}
