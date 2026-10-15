package org.example.constellation.core.analyze

import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ModuleDetectionTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `module comes from the gradle build layout`() {
        val out = FixtureCompiler.compile(
            tmp,
            mapOf(
                "demo/One.java" to """
                    package demo;
                    public class One { public void x() { } }
                """.trimIndent(),
            ),
            outRelative = "mymodule/build/classes",
        ).outDir
        val g = BytecodeAnalyzer(listOf(out)).analyze()
        assertEquals("mymodule", g.nodes.first { it.id == "demo.One" }.module)
    }

    @Test
    fun `unknown layout yields no module`() {
        // The rich fixture compiles into a plain `out` directory — no module detectable.
        val out = FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
        val g = BytecodeAnalyzer(listOf(out)).analyze()
        assertNull(g.nodes.first { it.id == "demo.model.Point" }.module)
    }

    @Test
    fun `externals have no module`() {
        val out = FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
        val g = BytecodeAnalyzer(listOf(out)).analyze()
        assertNull(g.nodes.first { it.id == "extlib.Util" }.module)
    }

    @Test
    fun `jar version suffix is stripped`() {
        assertEquals("kotlin-stdlib", ClassOrigins.moduleOf("/cache/kotlin-stdlib-2.4.20.jar!/kotlin/Unit.class"))
        assertEquals("fixture", ClassOrigins.moduleOf("/tmp/fixture.jar!/demo/A.class"))
        assertNull(ClassOrigins.moduleOf("/tmp/out/demo/A.class"))
    }

    @Test
    fun `analyzing one module pulls in the sibling modules`() {
        val api = FixtureCompiler.compile(
            tmp,
            mapOf(
                "demo/api/Api.java" to """
                    package demo.api;
                    public class Api { public void work() { } }
                """.trimIndent(),
            ),
            outRelative = "modA/build/classes",
        ).outDir
        val client = FixtureCompiler.compile(
            tmp,
            mapOf(
                "demo/client/Client.java" to """
                    package demo.client;
                    public class Client {
                        public void run() { new demo.api.Api().work(); }
                    }
                """.trimIndent(),
            ),
            outRelative = "modB/build/classes",
            classpath = listOf(api),
        ).outDir

        // Only modB's classes are passed in; modA must still be analyzed, not external,
        // so the cross-module call connects two ordinary nodes.
        val g = BytecodeAnalyzer(listOf(client)).analyze()
        val apiNode = g.nodes.first { it.id == "demo.api.Api" }
        assertEquals(NodeKind.CLASS, apiNode.kind)
        assertEquals("modA", apiNode.module)
        assertEquals("modB", g.nodes.first { it.id == "demo.client.Client" }.module)
        assertNotNull(
            g.edges.firstOrNull {
                it.from == "demo.client.Client" && it.to == "demo.api.Api" && it.type == EdgeType.CALL
            },
        )
    }

    @Test
    fun `test sources get the TEST category`() {
        val out = FixtureCompiler.compile(
            tmp,
            mapOf(
                "demo/ThingTest.java" to """
                    package demo;
                    public class ThingTest { public void check() { } }
                """.trimIndent(),
            ),
            outRelative = "modT/build/classes/test",
        ).outDir
        val g = BytecodeAnalyzer(listOf(out)).analyze()
        val n = g.nodes.first { it.id == "demo.ThingTest" }
        assertTrue(n.test)
        assertEquals(NodeCategory.TEST, n.category)
    }
}
