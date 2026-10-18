package org.example.constellation.core.analyze

import org.example.constellation.core.api.AnalysisRequest
import org.example.constellation.core.api.ClassRoot
import org.example.constellation.core.api.ProjectAnalyzer
import org.example.constellation.core.api.traits.DataModel

import org.example.constellation.core.api.model.ClassKind
import org.example.constellation.core.api.model.ClassOrigin
import org.example.constellation.core.api.model.CodeGraph
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.MethodTrait
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.Relation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProjectAnalyzerTest {

    @TempDir
    lateinit var tmp: Path

    private val demoDir: Path by lazy {
        FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
    }

    private fun analyze(vararg roots: ClassRoot = arrayOf(ClassRoot(demoDir, "fixture", "main"))): CodeGraph =
        ProjectAnalyzer().analyze(AnalysisRequest(roots.toList()))

    private fun node(graph: CodeGraph, id: String): Node? = graph.nodes.firstOrNull { it.id == id }

    private fun edge(graph: CodeGraph, from: String, to: String, type: Relation): Edge? =
        graph.edges.firstOrNull { it.from == from && it.to == to && it.type == type }

    @Test
    fun `kinds - class, interface, annotation, and enum-record are classes with traits`() {
        val g = analyze()
        assertEquals(ClassKind.CLASS, node(g, "demo.model.Point")?.kind)
        assertEquals(ClassKind.INTERFACE, node(g, "demo.model.Shape")?.kind)
        assertEquals(ClassKind.ANNOTATION, node(g, "demo.anno.Label")?.kind)
        assertEquals(ClassKind.CLASS, node(g, "demo.model.Color")?.kind) // enum folds into CLASS
        assertEquals(ClassKind.CLASS, node(g, "demo.model.Money")?.kind) // record folds into CLASS
    }

    @Test
    fun `origin carries module and source set labels from the root`() {
        val g = analyze(ClassRoot(demoDir, module = "mymod", sourceSet = "test"))
        val origin = node(g, "demo.model.Point")?.origin
        assertEquals(ClassOrigin.Project("mymod", "test"), origin)
    }

    @Test
    fun `extends is direct only`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.chain.A", "demo.chain.B", Relation.EXTENDS))
        assertNotNull(edge(g, "demo.chain.B", "demo.chain.C", Relation.EXTENDS))
        assertNull(edge(g, "demo.chain.A", "demo.chain.C", Relation.EXTENDS))
        assertNull(node(g, "java.lang.Object"))
        assertTrue(g.edges.none { it.to == "java.lang.Object" })
    }

    @Test
    fun `implements - two interfaces, and interface extending interface is extends`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.model.Circle", "demo.model.Shape", Relation.IMPLEMENTS))
        assertNotNull(edge(g, "demo.model.Circle", "demo.model.Resizable", Relation.IMPLEMENTS))
        assertNotNull(edge(g, "demo.iface.IB", "demo.iface.IA", Relation.EXTENDS))
        assertNull(edge(g, "demo.iface.IB", "demo.iface.IA", Relation.IMPLEMENTS))
    }

    @Test
    fun `call edges aggregate per pair with readable method signatures`() {
        val g = analyze()
        val e = edge(g, "demo.animal.DogService", "demo.animal.Dog", Relation.CALLS)
        assertNotNull(e)
        // new Dog() -> <init>; bark deduped; eat once; sorted by name
        assertEquals(listOf("<init>", "bark", "eat"), e!!.methods.map { it.signature.name })
        assertEquals("<init>(): void", e.methods.first().render())
        assertEquals("eat(): void", e.methods.last().render())
    }

    @Test
    fun `overloads differing only by parameter package stay distinct`() {
        // Both pick() overloads render identically ("pick(Mark): String") — the model
        // must still keep them apart (identity by fully-qualified types).
        val g = analyze()
        val e = edge(g, "demo.pick.PickerUser", "demo.pick.Picker", Relation.CALLS)
        assertNotNull(e)
        val picks = e!!.methods.filter { it.signature.name == "pick" }
        assertEquals(2, picks.size, "overloads collapsed: ${e.methods}")
    }

    @Test
    fun `constructor call via new is a call`() {
        val g = analyze()
        assertTrue(
            edge(g, "demo.animal.DogService", "demo.animal.Dog", Relation.CALLS)
                ?.methods?.any { it.signature.name == "<init>" } == true,
        )
        assertNotNull(edge(g, "demo.animal.DogService", "demo.animal.Animal", Relation.CALLS))
    }

    @Test
    fun `super constructor does not duplicate the extends edge`() {
        val g = analyze()
        assertNull(edge(g, "demo.animal.Dog", "demo.animal.Animal", Relation.CALLS))
        assertNotNull(edge(g, "demo.animal.Dog", "demo.animal.Animal", Relation.EXTENDS))
    }

    @Test
    fun `self edges are never emitted`() {
        val g = analyze()
        assertTrue(g.edges.none { it.from == it.to })
        assertNull(edge(g, "demo.animal.DogService", "demo.animal.DogService", Relation.CALLS))
    }

    @Test
    fun `static calls become CALLS_STATIC edges`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.stat.StatCaller", "demo.stat.FileUtils", Relation.CALLS_STATIC))
        assertNull(edge(g, "demo.stat.StatCaller", "demo.stat.FileUtils", Relation.CALLS))
        assertNotNull(edge(g, "demo.stat.StatCaller", "demo.stat.Counter", Relation.CALLS_STATIC))
    }

    @Test
    fun `method reference to a static method is CALLS_STATIC`() {
        val g = analyze()
        val e = edge(g, "demo.stat.StatCaller", "demo.stat.FileUtils", Relation.CALLS_STATIC)
        assertNotNull(e)
        // direct call FileUtils.ext(...) and the FileUtils::ext reference aggregate into one edge
        assertTrue(e!!.methods.any { it.render() == "ext(String): String" })
    }

    @Test
    fun `interface dispatch stays CALLS`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.stat.StatCaller", "java.util.function.Function", Relation.CALLS))
    }

    @Test
    fun `external static call is CALLS_STATIC`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.animal.DogService", "java.lang.String", Relation.CALLS_STATIC))
    }

    @Test
    fun `a pair can carry both CALLS and CALLS_STATIC`() {
        val g = analyze()
        assertTrue(
            edge(g, "demo.stat.StatCaller", "demo.app.AppException", Relation.CALLS_STATIC)
                ?.methods?.any { it.signature.name == "wrap" } == true,
        )
        assertTrue(
            edge(g, "demo.stat.StatCaller", "demo.app.AppException", Relation.CALLS)
                ?.methods?.any { it.signature.name == "<init>" } == true,
        )
    }

    @Test
    fun `no clinit call edges`() {
        val g = analyze()
        assertTrue(g.edges.flatMap { it.methods }.none { it.signature.name == "<clinit>" })
    }

    @Test
    fun `declared methods carry traits`() {
        val g = analyze()
        val point = node(g, "demo.model.Point")!!
        assertEquals(setOf(MethodTrait.GETTER), point.declaredMethods.first { it.signature.name == "getX" }.traits)
        assertEquals(setOf(MethodTrait.SETTER), point.declaredMethods.first { it.signature.name == "setX" }.traits)
        assertEquals(setOf(MethodTrait.CONSTRUCTOR), point.declaredMethods.first { it.signature.name == "<init>" }.traits)
        val ext = node(g, "demo.stat.FileUtils")!!.declaredMethods.first { it.signature.name == "ext" }
        assertTrue(MethodTrait.STATIC in ext.traits, "static declaration must carry STATIC: ${ext.traits}")
    }

    @Test
    fun `call records carry traits but never STATIC`() {
        val g = analyze()
        val dogInit = edge(g, "demo.animal.DogService", "demo.animal.Dog", Relation.CALLS)!!
            .methods.first { it.signature.name == "<init>" }
        assertEquals(setOf(MethodTrait.CONSTRUCTOR), dogInit.traits)

        val calls = edge(g, "demo.call.CallUser", "demo.call.CallTarget", Relation.CALLS)!!
        assertEquals(setOf(MethodTrait.GETTER), calls.methods.first { it.signature.name == "getX" }.traits)
        assertEquals(setOf(MethodTrait.SETTER), calls.methods.first { it.signature.name == "setX" }.traits)
        assertEquals(setOf(MethodTrait.GETTER), calls.methods.first { it.signature.name == "isReady" }.traits)
        assertEquals(emptySet<MethodTrait>(), calls.methods.first { it.signature.name == "getAt" }.traits)
        assertEquals(emptySet<MethodTrait>(), calls.methods.first { it.signature.name == "setTag" }.traits)

        val staticRecord = edge(g, "demo.call.CallUser", "demo.call.CallTarget", Relation.CALLS_STATIC)!!
            .methods.first { it.signature.name == "stat" }
        assertEquals(emptySet<MethodTrait>(), staticRecord.traits)
    }

    @Test
    fun `referenced classes outside roots become external`() {
        val g = analyze()
        val ext = node(g, "extlib.Util")
        assertNotNull(ext)
        assertEquals(ClassOrigin.External, ext?.origin)
        assertTrue(ext?.isExternal == true)
        assertEquals("extlib", ext?.pkg)
        assertNull(ext?.sourceFile)
        assertNull(ext?.origin?.let { it as? ClassOrigin.Project })
    }

    @Test
    fun `external nodes carry no traits and no methods`() {
        val g = analyze()
        val ext = node(g, "java.lang.String")!!
        assertTrue(ext.traits.isEmpty())
        assertTrue(ext.declaredMethods.isEmpty())
    }

    @Test
    fun `declared methods have readable signatures`() {
        val g = analyze()
        val methods = node(g, "demo.model.Point")!!.declaredMethods
        assertTrue(methods.any { it.render() == "getX(): int" })
        assertTrue(methods.any { it.render() == "setX(int): void" })
        assertTrue(methods.any { it.render() == "toString(): String" })
    }

    @Test
    fun `anonymous classes are excluded`() {
        val g = analyze()
        assertNull(node(g, "demo.animal.DogService\$1"))
        assertTrue(g.nodes.none { it.id.substringAfterLast('\$').firstOrNull()?.isDigit() == true })
    }

    @Test
    fun `java anonymous class calls are attributed to enclosing class`() {
        val g = analyze()
        val e = edge(g, "demo.animal.DogService", "extlib.Side", Relation.CALLS)
        assertNotNull(e)
        assertTrue(e!!.methods.any { it.signature.name == "ping" })
    }

    @Test
    fun `named nested classes are kept`() {
        val g = analyze()
        val inner = node(g, "demo.nested.Outer\$Inner")
        assertNotNull(inner)
        assertEquals(ClassKind.CLASS, inner?.kind)
        assertNotNull(edge(g, "demo.nested.Outer", "demo.nested.Outer\$Inner", Relation.CALLS))
    }

    @Test
    fun `method references through invokedynamic are captured`() {
        val g = analyze()
        val e = edge(g, "demo.ref.Ref", "demo.helper.Helper", Relation.CALLS)
        assertNotNull(e)
        assertTrue(e!!.methods.any { it.render() == "name(): String" })
    }

    @Test
    fun `duplicate roots - first wins and counted`() {
        val single = analyze()
        val root = ClassRoot(demoDir, "fixture", "main")
        val doubled = ProjectAnalyzer().analyze(AnalysisRequest(listOf(root, root)))
        assertEquals(single.nodes.map { it.id }, doubled.nodes.map { it.id })
        assertEquals(single.edges, doubled.edges)
        assertEquals(single.stats.analyzedClasses, doubled.stats.duplicatesSkipped)
    }

    @Test
    fun `directories are walked recursively`() {
        val g = analyze()
        assertNotNull(node(g, "demo.model.Point"))
        assertNotNull(node(g, "demo.animal.Dog"))
        assertNotNull(node(g, "demo.ref.Ref"))
    }

    @Test
    fun `analysis is deterministic`() {
        assertEquals(analyze(), analyze())
    }

    @Test
    fun `module-info and package-info class files are ignored`() {
        // Junk bytes prove the file is never even scanned.
        Files.write(demoDir.resolve("module-info.class"), byteArrayOf(1, 2, 3))
        Files.write(demoDir.resolve("package-info.class"), byteArrayOf(4, 5, 6))
        val g = analyze()
        assertTrue(g.nodes.none { it.simpleName.startsWith("module-info") || it.simpleName.startsWith("package-info") })
        assertTrue(g.stats.parseErrors.isEmpty(), "junk special files must not surface as parse errors")
    }

    @Test
    fun `model carries no jvm descriptors`() {
        val g = analyze()
        val descriptors = buildString {
            g.nodes.flatMap { it.declaredMethods }.forEach { append(it.render()) }
            g.edges.flatMap { it.methods }.forEach { append(it.render()) }
        }
        // no "L...;" encoding, no "()" followed by a single capital return code like ")V" with L-forms
        assertTrue(';' !in descriptors)
    }
}
