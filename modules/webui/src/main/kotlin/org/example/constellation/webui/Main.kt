package org.example.constellation.webui

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.webui.server.ConstellationServer
import java.awt.Desktop
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val settings = CliSettings.parse(args) ?: usage()
    val graph = AnalysisRunner().analyze(settings.paths)
    serve(graph, settings)
}

private fun serve(graph: CodeGraph, settings: CliSettings) {
    val uri = ConstellationServer(graph, settings.port).start()
    println("Constellation running at $uri")

    if (settings.openBrowser) {
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

private fun usage(): Nothing {
    System.err.println(
        """
        Usage: constellation <path>... [--port=N] [--no-browser]
          path — a .class file, a .jar/.zip, or a directory (walked recursively)
        Example: ./gradlew :webui:run --args="/path/to/project/build/classes"
        """.trimIndent(),
    )
    exitProcess(2)
}
