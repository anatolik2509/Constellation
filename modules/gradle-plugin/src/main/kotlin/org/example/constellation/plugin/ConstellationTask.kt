package org.example.constellation.plugin

import org.example.constellation.core.api.AnalysisRequest
import org.example.constellation.core.api.ClassRoot
import org.example.constellation.core.api.ProjectAnalyzer
import org.example.constellation.webui.server.ConstellationServer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.awt.Desktop

/**
 * Analyzes the project's compiled classes and serves the Constellation UI.
 * Depends on compilation through the source-set outputs wired into [classesDirs].
 */
abstract class ConstellationTask : DefaultTask() {

    /** Compiled classes (carries the compile-task dependencies). */
    @get:InputFiles
    abstract val classesDirs: ConfigurableFileCollection

    /** Labeled analysis roots — labels come from Gradle knowledge, not path sniffing. */
    @get:Internal
    abstract val rootSpecs: ListProperty<ClassRoot>

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val openBrowser: Property<Boolean>

    /** Stop the server after N seconds (0 = serve until interrupted). Test convenience. */
    @get:Input
    abstract val autoStopSeconds: Property<Int>

    @TaskAction
    fun serve() {
        val roots = rootSpecs.get().filter { it.dir.toFile().isDirectory }
        if (roots.isEmpty()) {
            logger.lifecycle("constellation: no compiled classes found — run the compile task first")
            return
        }

        val graph = ProjectAnalyzer().analyze(AnalysisRequest(roots))
        with(graph.stats) {
            logger.lifecycle(
                "constellation: analyzed $analyzedClasses classes from ${roots.size} root(s) — " +
                    "${graph.edges.size} relations, ${parseErrors.size} parse errors",
            )
            parseErrors.forEach { logger.warn("parse error: ${it.origin}: ${it.message}") }
        }

        val server = ConstellationServer(graph, port.get())
        val uri = server.start()
        logger.lifecycle("constellation running at $uri")

        if (openBrowser.get()) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri)
                }
            } catch (_: Throwable) {
                // Best effort only; the URL is logged above.
            }
        }

        val stopAfter = autoStopSeconds.get()
        if (stopAfter > 0) {
            Thread.sleep(stopAfter * 1000L)
            server.stop()
        } else {
            // Serve until the build is interrupted; stop the server cleanly on the way out.
            Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
            Thread.currentThread().join()
        }
    }
}
