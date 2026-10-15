package org.example.constellation.core.analyze

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Perf smoke: the analyzer over a real multi-package compiled output — this repo's own. */
@Tag("perf-smoke")
class PerfSmokeTest {

    @Test
    fun `own project - no crash, sizable graph, fast`() {
        val mainClasses = Path.of(
            ProjectAnalyzer::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val started = System.nanoTime()
        val g = ProjectAnalyzer()
            .analyze(AnalysisRequest(listOf(ClassRoot(mainClasses, "core", "main"))))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        println("perf-smoke result: ${g.nodes.size} nodes, ${g.edges.size} edges in $elapsedMs ms")
        assertTrue(g.stats.parseErrors.isEmpty(), "unexpected parse errors: ${g.stats.parseErrors.take(3)}")
        assertTrue(g.nodes.size > 100, "nodes=${g.nodes.size}, expected > 100")
        assertTrue(g.edges.size > 200, "edges=${g.edges.size}, expected > 200")
        assertTrue(elapsedMs < 60_000, "took ${elapsedMs}ms, expected < 60s")
    }
}
