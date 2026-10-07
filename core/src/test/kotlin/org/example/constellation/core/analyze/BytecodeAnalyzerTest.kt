package org.example.constellation.core.analyze

import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.NodeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.extension

class BytecodeAnalyzerTest {

    @TempDir
    lateinit var tmp: Path

    private val demoDir: Path by lazy {
        FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
    }

    private fun analyze(paths: List<Path> = listOf(demoDir)) = BytecodeAnalyzer(paths).analyze()

    private fun node(graph: org.example.constellation.core.model.CodeGraph, id: String) =
        graph.nodes.firstOrNull { it.id == id }

    private fun edge(graph: org.example.constellation.core.model.CodeGraph, from: String, to: String, type: EdgeType) =
        graph.edges.firstOrNull { it.from == from && it.to == to && it.type == type }

    @Test
    fun `kinds - class, interface, annotation, enum, record`() {
        val g = analyze()
        assertEquals(NodeKind.CLASS, node(g, "demo.model.Point")?.kind)
        assertTrue(node(g, "demo.model.Point")?.pojo == true)
        assertEquals(NodeKind.INTERFACE, node(g, "demo.model.Shape")?.kind)
        assertEquals(NodeKind.ANNOTATION, node(g, "demo.anno.Label")?.kind)
        // enum and record fold into CLASS
        assertEquals(NodeKind.CLASS, node(g, "demo.model.Color")?.kind)
        assertTrue(node(g, "demo.model.Color")?.pojo == false)
        assertEquals(NodeKind.CLASS, node(g, "demo.model.Money")?.kind)
        assertTrue(node(g, "demo.model.Money")?.pojo == true)
        assertEquals(NodeKind.EXTERNAL, node(g, "extlib.Util")?.kind)
    }

    @Test
    fun `inheritance is direct only`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.chain.A", "demo.chain.B", EdgeType.INHERITANCE))
        assertNotNull(edge(g, "demo.chain.B", "demo.chain.C", EdgeType.INHERITANCE))
        assertNull(edge(g, "demo.chain.A", "demo.chain.C", EdgeType.INHERITANCE))
        assertNull(node(g, "java.lang.Object"))
        assertTrue(g.edges.none { it.to == "java.lang.Object" })
    }

    @Test
    fun `implements - two interfaces, and interface extending interface is inheritance`() {
        val g = analyze()
        assertNotNull(edge(g, "demo.model.Circle", "demo.model.Shape", EdgeType.IMPLEMENTS))
        assertNotNull(edge(g, "demo.model.Circle", "demo.model.Resizable", EdgeType.IMPLEMENTS))
        assertNotNull(edge(g, "demo.iface.IB", "demo.iface.IA", EdgeType.INHERITANCE))
        assertNull(edge(g, "demo.iface.IB", "demo.iface.IA", EdgeType.IMPLEMENTS))
    }

    @Test
    fun `call edges aggregate per pair with deduped method list`() {
        val g = analyze()
        val e = edge(g, "demo.animal.DogService", "demo.animal.Dog", EdgeType.CALL)
        assertNotNull(e)
        // new Dog() -> <init>; bark deduped; eat once; sorted by name
        assertEquals(listOf("<init>", "bark", "eat"), e!!.methods.map { it.name })
    }

    @Test
    fun `constructor call via new is a call`() {
        val g = analyze()
        assertTrue(
            edge(g, "demo.animal.DogService", "demo.animal.Dog", EdgeType.CALL)
                ?.methods?.any { it.name == "<init>" } == true,
        )
        assertNotNull(edge(g, "demo.animal.DogService", "demo.animal.Animal", EdgeType.CALL))
    }

    @Test
    fun `super constructor does not duplicate inheritance edge`() {
        val g = analyze()
        assertNull(edge(g, "demo.animal.Dog", "demo.animal.Animal", EdgeType.CALL))
        assertNotNull(edge(g, "demo.animal.Dog", "demo.animal.Animal", EdgeType.INHERITANCE))
    }

    @Test
    fun `self edges are never emitted`() {
        val g = analyze()
        assertTrue(g.edges.none { it.from == it.to })
        // twice() calls walk() on itself
        assertNull(edge(g, "demo.animal.DogService", "demo.animal.DogService", EdgeType.CALL))
    }

    @Test
    fun `referenced classes outside input become external nodes`() {
        val g = analyze()
        val ext = node(g, "extlib.Util")
        assertNotNull(ext)
        assertEquals(NodeKind.EXTERNAL, ext?.kind)
        assertEquals("extlib", ext?.pkg)
        assertNull(ext?.sourceFile)

        val str = node(g, "java.lang.String")
        assertNotNull(str)
        assertEquals(NodeKind.EXTERNAL, str?.kind)
        assertEquals("java.lang", str?.pkg)
    }

    @Test
    fun `anonymous classes are excluded`() {
        val g = analyze()
        assertNull(node(g, "demo.animal.DogService\$1"))
        assertTrue(g.nodes.none { it.id.substringAfterLast('\$').firstOrNull()?.isDigit() == true })
        assertTrue(g.edges.none { it.from == "demo.animal.DogService\$1" || it.to == "demo.animal.DogService\$1" })
    }

    @Test
    fun `named nested classes are kept`() {
        val g = analyze()
        val inner = node(g, "demo.nested.Outer\$Inner")
        assertNotNull(inner)
        assertEquals(NodeKind.CLASS, inner?.kind)
        assertNotNull(edge(g, "demo.nested.Outer", "demo.nested.Outer\$Inner", EdgeType.CALL))
    }

    @Test
    fun `method references through invokedynamic are captured`() {
        val g = analyze()
        val e = edge(g, "demo.ref.Ref", "demo.helper.Helper", EdgeType.CALL)
        assertNotNull(e)
        assertTrue(e!!.methods.any { it.name == "name" })
    }

    @Test
    fun `jar input yields the same graph as directory input`() {
        val jar = tmp.resolve("fixture.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { jos ->
            Files.walk(demoDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension == "class" }
                    .sorted()
                    .forEach { f ->
                        val entry = JarEntry(demoDir.relativize(f).toString().replace('\\', '/'))
                        jos.putNextEntry(entry)
                        jos.write(Files.readAllBytes(f))
                        jos.closeEntry()
                    }
            }
        }
        val fromJar = BytecodeAnalyzer(listOf(jar)).analyze()
        val fromDir = analyze()
        assertEquals(fromDir.nodes.map { it.id }, fromJar.nodes.map { it.id })
        assertEquals(fromDir.edges, fromJar.edges)
    }

    @Test
    fun `duplicate fqn - first wins and counted`() {
        val single = analyze()
        val doubled = BytecodeAnalyzer(listOf(demoDir, demoDir)).analyze()
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
}
