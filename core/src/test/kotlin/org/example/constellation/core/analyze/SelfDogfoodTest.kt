package org.example.constellation.core.analyze

import org.example.constellation.core.model.EdgeType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Self-dogfood: the analyzer run on Constellation's own compiled core classes
 * (the `build/classes/kotlin/main` directory this test depends on anyway).
 */
class SelfDogfoodTest {

    private val graph by lazy {
        val mainClasses = Path.of(
            BytecodeAnalyzer::class.java.protectionDomain.codeSource.location.toURI(),
        )
        assertTrue(Files.isDirectory(mainClasses), "no compiled classes at $mainClasses")
        BytecodeAnalyzer(listOf(mainClasses)).analyze()
    }

    @Test
    fun `graph is non-empty and contains the analyzer classes`() {
        val g = graph
        assertTrue(g.nodes.isNotEmpty())
        assertTrue(g.edges.isNotEmpty())
        assertTrue(g.stats.parseErrors.isEmpty(), "unexpected parse errors: ${g.stats.parseErrors.take(3)}")

        for (id in listOf(
            "org.example.constellation.core.analyze.BytecodeAnalyzer",
            "org.example.constellation.core.analyze.ClassScanner",
            "org.example.constellation.core.analyze.ClassFileProvider",
            "org.example.constellation.core.analyze.ClassScanner\$ScannerVisitor",
            "org.example.constellation.core.filter.GraphFilter",
            "org.example.constellation.core.model.CodeGraph",
        )) {
            assertTrue(g.nodes.any { it.id == id }, "missing node $id")
        }
        assertTrue(
            g.edges.any { it.from == "org.example.constellation.core.analyze.BytecodeAnalyzer" },
            "no call edges out of BytecodeAnalyzer",
        )
    }

    @Test
    fun `comparator lambda calls are attributed to the enclosing class`() {
        // `analyze()` sorts with sortedBy/compareBy; kotlinc emits the comparators as
        // `BytecodeAnalyzer$analyze$$inlined$sortedBy$1`-style classes. BytecodeAnalyzer's own
        // bytecode never calls ComparisonsKt.compareValues directly — the edge below exists
        // only if the analyzer attributed the excluded comparator classes via EnclosingMethod.
        val e = graph.edges.firstOrNull {
            it.from == "org.example.constellation.core.analyze.BytecodeAnalyzer" &&
                it.to == "kotlin.comparisons.ComparisonsKt" &&
                it.type == EdgeType.CALL
        }
        assertNotNull(e, "no BytecodeAnalyzer -> ComparisonsKt call edge")
        assertTrue(e!!.methods.any { it.name == "compareValues" })
    }
}
