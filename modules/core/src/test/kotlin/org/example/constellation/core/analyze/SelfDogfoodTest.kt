package org.example.constellation.core.analyze

import org.example.constellation.core.api.AnalysisRequest
import org.example.constellation.core.api.ClassRoot
import org.example.constellation.core.api.ProjectAnalyzer

import org.example.constellation.core.api.model.Relation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Self-dogfood: the analyzer run on Constellation's own compiled core classes.
 */
class SelfDogfoodTest {

    private val graph by lazy {
        val mainClasses = Path.of(
            ProjectAnalyzer::class.java.protectionDomain.codeSource.location.toURI(),
        )
        assertTrue(Files.isDirectory(mainClasses), "no compiled classes at $mainClasses")
        ProjectAnalyzer().analyze(
            AnalysisRequest(listOf(ClassRoot(mainClasses, module = "core", sourceSet = "main"))),
        )
    }

    @Test
    fun `graph is non-empty and contains the analyzer classes`() {
        val g = graph
        assertTrue(g.nodes.isNotEmpty())
        assertTrue(g.edges.isNotEmpty())
        assertTrue(g.stats.parseErrors.isEmpty(), "unexpected parse errors: ${g.stats.parseErrors.take(3)}")

        for (id in listOf(
            "org.example.constellation.core.api.ProjectAnalyzer",
            "org.example.constellation.core.analyze.ClassScanner",
            "org.example.constellation.core.api.query.NodePredicates",
            "org.example.constellation.core.traits.builtin.DataModelProvider",
        )) {
            assertTrue(g.nodes.any { it.id == id }, "missing node $id")
        }
        assertTrue(
            g.edges.any { it.from == "org.example.constellation.core.api.ProjectAnalyzer" },
            "no call edges out of ProjectAnalyzer",
        )
    }

    @Test
    fun `origin labels flow from the root`() {
        val g = graph
        val analyzer = g.nodes.first { it.id.endsWith("ProjectAnalyzer") }
        assertEquals("core", (analyzer.origin as org.example.constellation.core.api.model.ClassOrigin.Project).module)
        assertEquals("main", (analyzer.origin as org.example.constellation.core.api.model.ClassOrigin.Project).sourceSet)
    }

    @Test
    fun `comparator lambda calls are attributed to the enclosing class`() {
        // `analyze()` sorts with sortedBy/compareBy; kotlinc emits the comparators as
        // `ProjectAnalyzer$analyze$$inlined$sortedBy$1`-style classes. The analyzer's own
        // bytecode never calls ComparisonsKt.compareValues directly — the edge below exists
        // only if the analyzer attributed the excluded comparator classes via EnclosingMethod.
        val e = graph.edges.firstOrNull {
            it.from == "org.example.constellation.core.api.ProjectAnalyzer" &&
                it.to == "kotlin.comparisons.ComparisonsKt" &&
                it.type == Relation.CALLS
        }
        assertNotNull(e, "no ProjectAnalyzer -> ComparisonsKt call edge")
        assertTrue(e!!.methods.any { it.name == "compareValues" })
    }
}
