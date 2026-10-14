package org.example.constellation.core.analyze

import org.example.constellation.core.model.NodeCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PojoDetectorTest {

    @TempDir
    lateinit var tmp: Path

    private fun analyzeRich() =
        BytecodeAnalyzer(listOf(FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo"))).analyze()

    private fun pojoFlag(g: org.example.constellation.core.model.CodeGraph, id: String) =
        g.nodes.first { it.id == id }.pojo

    @Test
    fun `pure dto with getters, setters and object overrides is pojo`() {
        val g = analyzeRich()
        assertTrue(pojoFlag(g, "demo.model.Point"))
        assertNotNull(g.nodes.first { it.id == "demo.model.Point" && it.category == NodeCategory.POJO })
    }

    @Test
    fun `record is pojo`() {
        assertTrue(pojoFlag(analyzeRich(), "demo.model.Money"))
    }

    @Test
    fun `dto with a business method is not pojo`() {
        assertFalse(pojoFlag(analyzeRich(), "demo.pojo.BusinessDto"))
    }

    @Test
    fun `dto with static factory is not pojo`() {
        assertFalse(pojoFlag(analyzeRich(), "demo.pojo.DtoWithStatic"))
    }

    @Test
    fun `getter without matching field is not pojo`() {
        assertFalse(pojoFlag(analyzeRich(), "demo.pojo.GhostGetter"))
    }

    @Test
    fun `enum is never pojo`() {
        assertFalse(pojoFlag(analyzeRich(), "demo.model.Color"))
    }

    @Test
    fun `kotlin data class shape is pojo`() {
        // copy/componentN/equals/hashCode/toString/getters — no javac equivalent, build raw info directly
        val info = rawClass(
            name = "kt/Data",
            fields = listOf("name", "age"),
            methods = listOf(
                Triple("<init>", "(Ljava/lang/String;I)V", 0),
                Triple("getName", "()Ljava/lang/String;", 0),
                Triple("getAge", "()I", 0),
                Triple("copy", "(Ljava/lang/String;I)Lkt/Data;", 0),
                Triple("component1", "()Ljava/lang/String;", 0),
                Triple("component2", "()I", 0),
                Triple("equals", "(Ljava/lang/Object;)Z", 0),
                Triple("hashCode", "()I", 0),
                Triple("toString", "()Ljava/lang/String;", 0),
            ),
        )
        assertTrue(PojoDetector.isPojo(info))
    }

    @Test
    fun `bridge and synthetic methods do not disqualify`() {
        val info = rawClass(
            name = "demo/ComparableDto",
            fields = listOf("value"),
            methods = listOf(
                Triple("<init>", "()V", 0),
                Triple("getValue", "()I", 0),
                // javac emits a synthetic bridge compareTo(Object) for Comparable
                Triple("compareTo", "(Ljava/lang/Object;)I", 0x1040), // ACC_BRIDGE | ACC_SYNTHETIC
            ),
        )
        assertTrue(PojoDetector.isPojo(info))
    }

    private fun rawClass(
        name: String,
        fields: List<String>,
        methods: List<Triple<String, String, Int>>,
    ) = RawClassInfo(
        name = name,
        access = org.objectweb.asm.Opcodes.ACC_PUBLIC,
        superName = "java/lang/Object",
        interfaces = listOf("java/lang/Comparable"),
        instanceFields = fields,
        recordComponents = emptyList(),
        methods = methods.map { RawMethod(it.first, it.second, it.third) },
        calls = emptyList(),
        sourceFile = null,
    )
}

class ClassScannerTest {

    @Test
    fun `synthetic class is excluded from nodes`(@TempDir tmp: Path) {
        val cw = org.objectweb.asm.ClassWriter(0)
        cw.visit(
            org.objectweb.asm.Opcodes.V1_8,
            org.objectweb.asm.Opcodes.ACC_PUBLIC or org.objectweb.asm.Opcodes.ACC_SYNTHETIC,
            "synth/S",
            null,
            "java/lang/Object",
            null,
        )
        cw.visitEnd()
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir.resolve("synth"))
        Files.write(dir.resolve("synth/S.class"), cw.toByteArray())

        val g = BytecodeAnalyzer(listOf(dir)).analyze()
        assertTrue(g.nodes.isEmpty())
        assertEquals(1, g.stats.skippedSynthetic)
    }

    @Test
    fun `anonymous inner class pattern is excluded`() {
        assertTrue(BytecodeAnalyzer.isExcludedClassName("demo/DogService\$1"))
        assertTrue(BytecodeAnalyzer.isExcludedClassName("demo/MainKt\$main\$1"))
        assertFalse(BytecodeAnalyzer.isExcludedClassName("demo/Outer\$Inner"))
        assertFalse(BytecodeAnalyzer.isExcludedClassName("demo/Outer"))
    }
}
