package org.example.constellation.core.properties

import org.example.constellation.core.analyze.AnalysisRequest
import org.example.constellation.core.analyze.ClassRoot
import org.example.constellation.core.analyze.FixtureCompiler
import org.example.constellation.core.analyze.ProjectAnalyzer
import org.example.constellation.core.analyze.RICH_FIXTURE
import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class StaticComponentTest {

    @TempDir
    lateinit var tmp: Path

    private fun analyze(): CodeGraph {
        val out = FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
        return ProjectAnalyzer().analyze(AnalysisRequest(listOf(ClassRoot(out, "fixture", "main"))))
    }

    private fun staticComponent(n: Node): StaticComponent? =
        n.properties.filterIsInstance<StaticComponent>().firstOrNull()

    @Test
    fun `singleton with static INSTANCE is an object`() {
        val sc = staticComponent(analyze().nodes.first { it.id == "demo.stat.Singleton" })
        assertNotNull(sc)
        assertEquals(StaticComponent.CarrierKind.OBJECT, sc!!.carrier)
    }

    @Test
    fun `java static nested class is detected`() {
        val sc = staticComponent(analyze().nodes.first { it.id == "demo.stat.Outer\$Util" })
        assertNotNull(sc)
        assertEquals(StaticComponent.CarrierKind.STATIC_NESTED, sc!!.carrier)
    }

    @Test
    fun `plain inner class is not static`() {
        assertNull(staticComponent(analyze().nodes.first { it.id == "demo.stat.Outer\$Inner" }))
    }

    @Test
    fun `all-static utility class is a static holder`() {
        val sc = staticComponent(analyze().nodes.first { it.id == "demo.stat.FileUtils" })
        assertNotNull(sc)
        assertEquals(StaticComponent.CarrierKind.STATIC_HOLDER, sc!!.carrier)
    }

    @Test
    fun `plain instance class, enum and record are not static components`() {
        val g = analyze()
        assertNull(staticComponent(g.nodes.first { it.id == "demo.animal.DogService" }))
        assertNull(staticComponent(g.nodes.first { it.id == "demo.model.Color" })) // enum -> DataModel only
        assertNull(staticComponent(g.nodes.first { it.id == "demo.model.Money" })) // record -> DataModel only
    }

    @Test
    fun `static state plus static behavior is still a static holder`() {
        val sc = staticComponent(analyze().nodes.first { it.id == "demo.stat.Counter" })
        assertNotNull(sc)
        assertEquals(StaticComponent.CarrierKind.STATIC_HOLDER, sc!!.carrier)
    }

    @Test
    fun `constants-only class is a static holder`() {
        val sc = staticComponent(analyze().nodes.first { it.id == "demo.stat.Consts" })
        assertNotNull(sc)
        assertEquals(StaticComponent.CarrierKind.STATIC_HOLDER, sc!!.carrier)
    }

    @Test
    fun `INSTANCE of another type does not make a singleton`() {
        assertNull(staticComponent(analyze().nodes.first { it.id == "demo.stat.ClockHolder" }))
    }

    @Test
    fun `nested record is not a static component despite the implicit static flag`() {
        val n = analyze().nodes.first { it.id == "demo.stat.Wrapper\$P" }
        assertNull(staticComponent(n))
        // it is still a data model
        assertEquals(
            org.example.constellation.core.properties.DataModel.CarrierKind.JAVA_RECORD,
            n.properties.filterIsInstance<org.example.constellation.core.properties.DataModel>().first().carrier,
        )
    }

    @Test
    fun `subclass of a stateful superclass is not a static holder`() {
        assertNull(staticComponent(analyze().nodes.first { it.id == "demo.app.AppException" }))
    }

    @Test
    fun `defaults register the static provider`() {
        assertTrue(PropertyRegistry.defaults().all().any { it.id == StaticComponent.ID })
    }
}
