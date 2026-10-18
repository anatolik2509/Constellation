package org.example.constellation.plugin

import org.example.constellation.core.api.ClassRoot
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * `org.example.constellation`: registers the `constellation` task, which depends
 * on compilation and analyzes its outputs.
 *
 * ```kotlin
 * plugins { id("org.example.constellation") }
 * constellation { includeSubprojects = true }   // whole-build view from the root
 * ```
 */
class ConstellationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("constellation", ConstellationExtension::class.java).apply {
            port.convention(8080)
            openBrowser.convention(true)
            includeTests.convention(false)
            includeSubprojects.convention(false)
            autoStopSeconds.convention(0)
        }

        // The task always exists (applying to a project without java must not fail silently);
        // source-set wiring happens once the java plugin shows up.
        val task = project.tasks.register("constellation", ConstellationTask::class.java) { t ->
            t.group = "help"
            t.description = "Visualize the project's compiled classes as a graph (Constellation UI)"
            t.port.set(extension.port)
            t.openBrowser.set(extension.openBrowser)
            t.autoStopSeconds.set(extension.autoStopSeconds)
        }

        project.plugins.withId("java") {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            // Read includeTests lazily: users configure the extension after `plugins {}`.
            val mainOutput = sourceSets.findByName("main")?.output
            if (mainOutput != null) {
                task.configure { t ->
                    t.classesDirs.from(mainOutput)
                    t.rootSpecs.addAll(mainOutput.classesDirs.map { ClassRoot(it.toPath(), project.name, "main") })
                }
            }
            task.configure { t ->
                val empty = project.files()
                t.classesDirs.from(
                    project.provider {
                        if (extension.includeTests.get()) sourceSets.findByName("test")?.output ?: empty else empty
                    },
                )
                t.rootSpecs.addAll(
                    project.provider {
                        if (!extension.includeTests.get()) return@provider emptyList()
                        val output = sourceSets.findByName("test")?.output ?: return@provider emptyList()
                        output.classesDirs.map { ClassRoot(it.toPath(), project.name, "test") }
                    },
                )
            }
        }

        // Whole-build view: pull every java subproject's main classes into the root's task.
        // NB: relies on subprojects being configured — with configuration-on-demand a
        // skipped subproject contributes nothing.
        project.subprojects.forEach { sub ->
            sub.afterEvaluate {
                if (!extension.includeSubprojects.get()) return@afterEvaluate
                sub.plugins.withId("java") {
                    val sourceSets = sub.extensions.findByType(SourceSetContainer::class.java)
                    val mainOutput = sourceSets?.findByName("main")?.output ?: return@withId
                    task.configure { t ->
                        t.classesDirs.from(mainOutput)
                        t.rootSpecs.addAll(mainOutput.classesDirs.map { ClassRoot(it.toPath(), sub.name, "main") })
                    }
                }
            }
        }
    }
}
