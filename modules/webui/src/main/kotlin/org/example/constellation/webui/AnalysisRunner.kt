package org.example.constellation.webui

import org.example.constellation.core.analyze.BytecodeAnalyzer
import org.example.constellation.core.model.CodeGraph
import java.nio.file.Path
import java.io.PrintStream

/** Runs the bytecode analysis for the CLI and prints the human-readable report. */
class AnalysisRunner(private val out: PrintStream = System.out) {

    fun analyze(paths: List<Path>): CodeGraph {
        val graph = BytecodeAnalyzer(paths).analyze()
        printReport(graph, paths)
        return graph
    }

    private fun printReport(graph: CodeGraph, paths: List<Path>) {
        with(graph.stats) {
            out.println(
                "Analyzed $analyzedClasses classes from ${paths.size} path(s) — " +
                    "${graph.edges.size} edges, ${parseErrors.size} parse errors",
            )
            for (err in parseErrors) {
                out.println("  parse error: ${err.origin}: ${err.message}")
            }
            if (analyzedClasses == 0) {
                out.println(
                    "Warning: no classes found under $paths — serving an empty graph. " +
                        "Did you build the target project?",
                )
            }
        }
    }
}
