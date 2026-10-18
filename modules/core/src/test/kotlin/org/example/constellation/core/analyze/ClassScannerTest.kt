package org.example.constellation.core.analyze

import org.example.constellation.core.api.AnalysisRequest
import org.example.constellation.core.api.ClassRoot
import org.example.constellation.core.api.ProjectAnalyzer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

        val g = ProjectAnalyzer().analyze(AnalysisRequest(listOf(ClassRoot(dir, "fixture", "main"))))
        assertTrue(g.nodes.isEmpty())
        assertEquals(1, g.stats.skippedSynthetic)
    }

    @Test
    fun `anonymous inner class pattern is excluded`() {
        assertTrue(ProjectAnalyzer.isExcludedClassName("demo/DogService\$1"))
        assertTrue(ProjectAnalyzer.isExcludedClassName("demo/MainKt\$main\$1"))
        assertTrue(!ProjectAnalyzer.isExcludedClassName("demo/Outer\$Inner"))
        assertTrue(!ProjectAnalyzer.isExcludedClassName("demo/Outer"))
    }
}
