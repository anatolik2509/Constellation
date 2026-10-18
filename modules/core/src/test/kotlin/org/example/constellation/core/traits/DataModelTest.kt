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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DataModelTest {

    @TempDir
    lateinit var tmp: Path

    private fun analyze(): CodeGraph {
        val out = FixtureCompiler.compile(tmp, RICH_FIXTURE).outDir.resolve("demo")
        return ProjectAnalyzer().analyze(AnalysisRequest(listOf(ClassRoot(out, "fixture", "main"))))
    }

    private fun dataModel(n: Node): DataModel? = n.properties.filterIsInstance<DataModel>().firstOrNull()

    @Test
    fun `java bean is a data model`() {
        val dm = dataModel(analyze().nodes.first { it.id == "demo.model.Point" })
        assertNotNull(dm)
        assertEquals(DataModel.CarrierKind.JAVA_BEAN, dm!!.carrier)
        assertEquals(listOf("x", "y"), dm.components)
    }

    @Test
    fun `bean with reference-typed setter is a data model`() {
        // Regression guard: setName(String) used to be rejected by a param-counting bug,
        // silently dropping DataModel from the most common bean shape.
        val dm = dataModel(analyze().nodes.first { it.id == "demo.model.User" })
        assertNotNull(dm)
        assertEquals(DataModel.CarrierKind.JAVA_BEAN, dm!!.carrier)
        assertEquals(listOf("name"), dm.components)
    }

    @Test
    fun `copy method alone does not make a kotlin data class`() {
        val dm = dataModel(analyze().nodes.first { it.id == "demo.model.Report" })
        assertNotNull(dm)
        assertEquals(DataModel.CarrierKind.JAVA_BEAN, dm!!.carrier)
    }

    @Test
    fun `record is a data model with record components`() {
        val dm = dataModel(analyze().nodes.first { it.id == "demo.model.Money" })
        assertNotNull(dm)
        assertEquals(DataModel.CarrierKind.JAVA_RECORD, dm!!.carrier)
        assertEquals(listOf("amount", "currency"), dm.components)
    }

    @Test
    fun `enum is a data model`() {
        val dm = dataModel(analyze().nodes.first { it.id == "demo.model.Color" })
        assertNotNull(dm)
        assertEquals(DataModel.CarrierKind.ENUM, dm!!.carrier)
    }

    @Test
    fun `class with business method is not a data model`() {
        assertTrue(dataModel(analyze().nodes.first { it.id == "demo.pojo.BusinessDto" }) == null)
    }

    @Test
    fun `class with static factory is not a data model`() {
        assertTrue(dataModel(analyze().nodes.first { it.id == "demo.pojo.DtoWithStatic" }) == null)
    }

    @Test
    fun `getter without matching field is not a data model`() {
        assertTrue(dataModel(analyze().nodes.first { it.id == "demo.pojo.GhostGetter" }) == null)
    }

    @Test
    fun `properties are sorted by id`() {
        val g = analyze()
        assertTrue(
            g.nodes.all { n -> n.properties.map { it.id.value } == n.properties.map { it.id.value }.sorted() },
        )
    }
}
