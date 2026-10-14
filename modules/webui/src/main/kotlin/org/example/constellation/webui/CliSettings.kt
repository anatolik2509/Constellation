package org.example.constellation.webui

import org.example.constellation.webui.server.ConstellationServer.Companion.DEFAULT_PORT
import java.nio.file.Files
import java.nio.file.Path

/** Parsed command line: what to analyze and how to serve it. */
data class CliSettings(
    /** `.class` files, jars, or directories (walked recursively). */
    val paths: List<Path>,
    /** Server port; 0 = a free ephemeral port. */
    val port: Int,
    val openBrowser: Boolean,
) {
    companion object {

        /** Returns null when no usable paths were given (the caller prints usage). */
        fun parse(args: Array<String>): CliSettings? {
            val paths = args.asSequence()
                .filter { !it.startsWith("--") }
                .map { Path.of(it) }
                .filter { Files.exists(it) }
                .toList()
            if (paths.isEmpty()) return null

            val port = args.firstOrNull { it.startsWith("--port=") }
                ?.removePrefix("--port=")
                ?.toIntOrNull()
                ?: DEFAULT_PORT
            return CliSettings(paths, port, openBrowser = "--no-browser" !in args)
        }
    }
}
