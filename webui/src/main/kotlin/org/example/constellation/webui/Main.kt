package org.example.constellation.webui

import org.example.constellation.core.analyze.BytecodeAnalyzer
import org.example.constellation.webui.server.ConstellationServer
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val port = args.firstOrNull { it.startsWith("--port=") }?.removePrefix("--port=")?.toIntOrNull() ?: 0
    val noBrowser = "--no-browser" in args
    val paths = args.filter { !it.startsWith("--") }
        .map { Path.of(it) }
        .filter { Files.exists(it) }

    if (paths.isEmpty()) {
        System.err.println(
            """
            Usage: constellation <path>... [--port=N] [--no-browser]
              path — a .class file, a .jar/.zip, or a directory (walked recursively)
            Example: ./gradlew :ui:run --args="/path/to/project/build/classes"
            """.trimIndent(),
        )
        exitProcess(2)
    }

    val graph = BytecodeAnalyzer(paths).analyze()
    with(graph.stats) {
        println("Analyzed $analyzedClasses classes from ${paths.size} path(s) — ${graph.edges.size} edges, ${parseErrors.size} parse errors")
        for (err in parseErrors) {
            println("  parse error: ${err.origin}: ${err.message}")
        }
        if (analyzedClasses == 0) {
            println("Warning: no classes found under $paths — serving an empty graph. Did you build the target project?")
        }
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
