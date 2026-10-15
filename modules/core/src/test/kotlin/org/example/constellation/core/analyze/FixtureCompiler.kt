package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.writeText

/** Compiles inline Java fixtures with the system javac (`--release 17`) into a temp output dir. */
object FixtureCompiler {

    class Result(val srcDir: Path, val outDir: Path)

    /** [outRelative] controls the output layout — e.g. `mymodule/build/classes` for module-detection tests. */
    fun compile(
        base: Path,
        sources: Map<String, String>,
        outRelative: String = "out",
        classpath: List<Path> = emptyList(),
    ): Result {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("system Java compiler unavailable")
        val srcDir = base.resolve("src")
        val outDir = base.resolve(outRelative)
        val files = sources.map { (rel, text) ->
            val f = srcDir.resolve(rel)
            Files.createDirectories(f.parent)
            f.writeText(text)
            f
        }
        val args = mutableListOf("--release", "17", "-d", outDir.toString())
        if (classpath.isNotEmpty()) {
            args += listOf("-classpath", classpath.joinToString(":") { it.toString() })
        }
        val rc = compiler.run(null, null, null, *(args + files.map { it.toString() }).toTypedArray())
        require(rc == 0) { "javac failed with rc=$rc" }
        return Result(srcDir, outDir)
    }
}
