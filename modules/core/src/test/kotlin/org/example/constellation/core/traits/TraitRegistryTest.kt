package org.example.constellation.core.properties

import org.example.constellation.core.analyze.AnalysisRequest
import org.example.constellation.core.analyze.ClassRoot
import org.example.constellation.core.analyze.FixtureCompiler
import org.example.constellation.core.analyze.ProjectAnalyzer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PropertyRegistryTest {

    @Test
    fun `defaults contain the built-in data model provider`() {
        val registry = PropertyRegistry.defaults()
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
                registry = PropertyRegistry.defaults().also { it.register(EverythingProvider) },
            ),
        )

        assertTrue(graph.nodes.first { it.id == "demo.A" }.properties.any { it.id.value == "test.everything" })
        assertTrue(graph.nodes.first { it.id == "other.B" }.properties.none { it.id.value == "test.everything" })
    }

    @Test
    fun `discover loads service-loader providers`() {
        // TestFixtureProvider is registered in test resources via META-INF/services
        val registry = PropertyRegistry.discover()
        assertTrue(registry.all().any { it.id.value == "test.fixture.marker" })
    }
}
