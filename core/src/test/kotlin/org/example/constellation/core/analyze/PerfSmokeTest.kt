package org.example.constellation.core.analyze

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Perf smoke: the analyzer against a real large jar (kotlin-stdlib from the test classpath).
 * Runs as part of the normal suite; exclude with `./gradlew test -PskipPerfSmoke`.
 */
@Tag("perf-smoke")
class PerfSmokeTest {

    @Test
    fun `kotlin-stdlib - no crash, sizable graph, under a minute`() {
        val stdlib = kotlinStdlibJar()
        assumeTrue(stdlib != null, "kotlin-stdlib jar not found on the test classpath")
        println("perf-smoke input: $stdlib (${Files.size(stdlib!!) / 1024} KiB)")

        val started = System.nanoTime()
        val g = BytecodeAnalyzer(listOf(stdlib)).analyze()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        println(
            "perf-smoke result: ${g.nodes.size} nodes, ${g.edges.size} edges, " +
                "${g.stats.analyzedClasses} analyzed, ${g.stats.skippedSynthetic} synthetic skipped " +
                "in $elapsedMs ms",
        )
        assertTrue(g.stats.parseErrors.isEmpty(), "unexpected parse errors: ${g.stats.parseErrors.take(3)}")
        assertTrue(g.nodes.size > 800, "nodes=${g.nodes.size}, expected > 800")
        assertTrue(g.edges.size > 2_000, "edges=${g.edges.size}, expected > 2000")
        assertTrue(elapsedMs < 60_000, "took ${elapsedMs}ms, expected < 60s")
    }

    /** `kotlin.KotlinVersion` lives in kotlin-stdlib; its code source points at the jar. */
    private fun kotlinStdlibJar(): Path? = runCatching {
        Path.of(KotlinVersion::class.java.protectionDomain.codeSource.location.toURI())
    }.getOrNull()?.takeIf { Files.isRegularFile(it) && it.extension.equals("jar", ignoreCase = true) }
}
