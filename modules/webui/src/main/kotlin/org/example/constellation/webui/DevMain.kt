package org.example.constellation.webui

import org.example.constellation.core.analyze.AnalysisRequest
import org.example.constellation.core.analyze.ClassRoot
import org.example.constellation.core.analyze.ProjectAnalyzer
import org.example.constellation.webui.server.ConstellationServer
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Dev-only dogfood entry — NOT the supported way to run the tool (that is the
 * Gradle plugin). Takes compiled-classes directories shaped
 * `.../<module>/build/classes/<lang>/<source-set>` and serves the graph.
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull { it.startsWith("--port=") }?.removePrefix("--port=")?.toIntOrNull() ?: 8080
    val noBrowser = "--no-browser" in args

    val roots = args.asSequence()
        .filter { !it.startsWith("--") }
        .map { Path.of(it).toAbsolutePath().normalize() }
        .filter { Files.isDirectory(it) }
        .mapNotNull { dir -> rootOf(dir) }
        .toList()

    if (roots.isEmpty()) {
        System.err.println("Usage (dev): devMain <module-classes-dir>... [--port=N] [--no-browser]")
        exitProcess(2)
    }

    val graph = ProjectAnalyzer().analyze(AnalysisRequest(roots))
    with(graph.stats) {
        println("Analyzed $analyzedClasses classes — ${graph.edges.size} relations, ${parseErrors.size} parse errors")
        parseErrors.forEach { println("  parse error: $it") }
    }

    val uri = ConstellationServer(graph, port).start()
    println("Constellation running at $uri")

    if (!noBrowser) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
            }
        } catch (_: Throwable) {
            // Best effort only; the URL is printed above.
        }
    }
    Thread.currentThread().join()
}

/** `…/<module>/build/classes/<lang>/<source-set>` → labeled [ClassRoot]. */
private fun rootOf(dir: Path): ClassRoot? {
    val sourceSet = dir.fileName?.toString() ?: return null
    val classesDir = dir.parent?.parent ?: return null // <source-set> → <lang> → classes
    if (classesDir.fileName?.toString() != "classes") return null
    val moduleDir = classesDir.parent?.parent ?: return null // skip `build`, land on the module dir
    return ClassRoot(
        dir,
        module = moduleDir.fileName?.toString() ?: return null,
        sourceSet = sourceSet,
    )
}
