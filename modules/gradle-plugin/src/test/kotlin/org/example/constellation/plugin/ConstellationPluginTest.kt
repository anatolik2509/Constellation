package org.example.constellation.plugin

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConstellationPluginTest {

    @TempDir
    lateinit var tmp: Path

    private fun project() = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()!!

    @Test
    fun `registers the constellation task with defaults`() {
        val project = project()
        project.plugins.apply("java")
        project.plugins.apply(ConstellationPlugin::class.java)

        // findByName realizes the task, running its configuration action
        val task = project.tasks.findByName("constellation") as ConstellationTask
        assertNotNull(task)
        assertEquals("help", task.group)
        assertEquals(8080, task.port.get())
        assertTrue(task.openBrowser.get())
        assertFalse(task.hasTestRoots()) // includeTests defaults to false
        assertEquals(0, task.autoStopSeconds.get())
    }

    @Test
    fun `main source set is wired with labeled roots`() {
        val project = project()
        project.plugins.apply("java")
        project.plugins.apply(ConstellationPlugin::class.java)

        val task = project.tasks.findByName("constellation") as ConstellationTask

        val roots = task.rootSpecs.get()
        assertTrue(roots.isNotEmpty(), "no roots wired")
        assertTrue(roots.all { it.module == project.name })
        assertTrue(roots.any { it.sourceSet == "main" })
        assertTrue(roots.none { it.sourceSet == "test" })

        val mainClasses = project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main").output.classesDirs
        assertTrue(roots.any { root -> mainClasses.any { it == root.dir.toFile() } })
    }

    @Test
    fun `extension overrides flow into the task`() {
        val project = project()
        project.plugins.apply("java")
        project.plugins.apply(ConstellationPlugin::class.java)
        project.extensions.configure(ConstellationExtension::class.java) {
            it.port.set(9999)
            it.includeTests.set(true)
        }

        val task = project.tasks.findByName("constellation") as ConstellationTask
        assertEquals(9999, task.port.get())
        assertTrue(task.hasTestRoots())
    }

    @Test
    fun `task registers even without the java plugin`() {
        val project = project()
        project.plugins.apply(ConstellationPlugin::class.java)
        val task = project.tasks.findByName("constellation")
        assertNotNull(task)
        // and the extension DSL is available
        project.extensions.configure(ConstellationExtension::class.java) { it.port.set(7) }
    }

    @Test
    fun `includeSubprojects pulls subproject main classes into the root task`() {
        val root = project()
        val sub = ProjectBuilder.builder().withParent(root).withName("sub").build()!!
        root.plugins.apply("java")
        sub.plugins.apply("java") // create sub's source sets before the plugin's afterEvaluate hooks
        root.plugins.apply(ConstellationPlugin::class.java)
        root.extensions.configure(ConstellationExtension::class.java) {
            it.includeSubprojects.set(true)
        }

        (sub as org.gradle.api.internal.project.ProjectInternal).evaluate()
        val task = root.tasks.findByName("constellation") as ConstellationTask

        assertTrue(task.rootSpecs.get().any { it.module == "sub" && it.sourceSet == "main" })
        assertTrue(task.rootSpecs.get().any { it.module == root.name && it.sourceSet == "main" })
    }

    private fun ConstellationTask.hasTestRoots(): Boolean =
        rootSpecs.get().any { it.sourceSet == "test" }
}
