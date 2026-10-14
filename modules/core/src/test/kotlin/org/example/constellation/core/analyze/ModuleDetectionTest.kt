package org.example.constellation.core.analyze

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        assertEquals("kotlin-stdlib", ModuleNames.of("/cache/kotlin-stdlib-2.4.20.jar!/kotlin/Unit.class"))
        assertEquals("fixture", ModuleNames.of("/tmp/fixture.jar!/demo/A.class"))
        assertNull(ModuleNames.of("/tmp/out/demo/A.class"))
    }
}
