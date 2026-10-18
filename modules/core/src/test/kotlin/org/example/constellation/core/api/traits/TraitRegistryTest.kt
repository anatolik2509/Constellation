package org.example.constellation.core.api.traits

import org.example.constellation.core.api.AnalysisRequest
import org.example.constellation.core.api.ClassRoot
import org.example.constellation.core.analyze.FixtureCompiler
import org.example.constellation.core.api.ProjectAnalyzer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TraitRegistryTest {

    @Test
    fun `defaults contain the built-in data model provider`() {
        val registry = TraitRegistry.defaults()
        assertTrue(registry.all().any { it.id == DataModel.ID })
        assertTrue(registry.all().all { p -> p.displayName.isNotBlank() })
    }

    @Test
    fun `custom provider registers and detects`() {
        val out = FixtureCompiler.compile(
            Files.createTempDirectory("props"),
            mapOf(
                "demo/A.java" to "package demo; public class A { public void x() { } }",
                "other/B.java" to "package other; public class B { public void y() { } }",
            ),
        ).outDir
        val graph = ProjectAnalyzer().analyze(
            AnalysisRequest(
                listOf(ClassRoot(out, "fixture", "main")),
                registry = TraitRegistry.defaults().also { it.register(EverythingProvider) },
            ),
        )

        assertTrue(graph.nodes.first { it.id == "demo.A" }.traits.any { it.id.value == "test.everything" })
        assertTrue(graph.nodes.first { it.id == "other.B" }.traits.none { it.id.value == "test.everything" })
    }

    @Test
    fun `discover loads service-loader providers`() {
        // TestFixtureProvider is registered in test resources via META-INF/services
        val registry = TraitRegistry.discover()
        assertTrue(registry.all().any { it.id.value == "test.fixture.marker" })
    }
}
