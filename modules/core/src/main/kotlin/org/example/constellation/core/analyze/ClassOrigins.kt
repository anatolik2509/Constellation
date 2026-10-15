package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path

/**
 * Heuristics over a class file's origin (the file/jar path it was read from):
 * which module it belongs to, whether it is a compiled test source, and — given
 * a path inside some module's output — the class roots of the whole project.
 */
internal object ClassOrigins {

    private val VERSION_SUFFIX = Regex("-\\d+(\\.\\d+)*$")

    /**
     * Best-effort module name: Gradle/Maven layouts (`<module>/build/classes/…`,
     * `<module>/src/…`) or a jar name with the version suffix stripped
     * (`kotlin-stdlib-2.4.20.jar` -> `kotlin-stdlib`).
     */
    fun moduleOf(origin: String): String? {
        val filePath = origin.substringBefore("!/")
        val segments = filePath.split('/', '\\')

        val build = segments.indexOfFirst { it.equals("build", ignoreCase = true) }
        if (build > 0) return segments[build - 1]
        val src = segments.indexOfFirst { it.equals("src", ignoreCase = true) }
        if (src > 0) return segments[src - 1]

        if (filePath.endsWith(".jar", ignoreCase = true)) {
            return segments.last().removeSuffix(".jar").replace(VERSION_SUFFIX, "")
        }
        return null
    }

    /** `…/build/classes/[lang/]test/…` (Gradle) or `…/target/test-classes/…` (Maven). */
    fun isTest(origin: String): Boolean {
        val segments = origin.substringBefore("!/").split('/', '\\')
        for (i in 0 until segments.size - 1) {
            val seg = segments[i].lowercase()
            val next = segments[i + 1].lowercase()
            val isGradleOutput = seg == "build" && next == "classes"
            val isMavenOutput = seg == "target" && (next == "classes" || next == "test-classes")
            if (isMavenOutput && next == "test-classes") return true
            if (isGradleOutput || isMavenOutput) {
                // build/classes/<lang?>/test — skip the optional language dir
                if (segments.getOrNull(i + 2)?.lowercase() == "test") return true
                if (segments.getOrNull(i + 3)?.lowercase() == "test") return true
                return false
            }
        }
        return false
    }

    /**
     * Expands the input paths so that a path pointing into one module's output
     * (`…/<module>/build/classes/…`) also brings in the compiled classes of the
     * sibling modules: cross-module links then connect analyzed classes instead
     * of ending at hidden EXTERNAL nodes. Paths without a module-output marker
     * (a repo root, a standalone jar) pass through unchanged.
     */
    fun expandProjectClassRoots(paths: List<Path>): List<Path> {
        // NB: Path implements Iterable<Path>, so `+=` overload resolution is ambiguous — use add().
        // Multiplicity is preserved: the same root given twice must still count as duplicates.
        val expanded = mutableListOf<Path>()
        for (path in paths) {
            val moduleDir = moduleDirOf(path)
            val projectDir = moduleDir?.parent
            if (moduleDir == null || projectDir == null) {
                expanded.add(path)
                continue
            }
            for (sibling in projectDirectories(projectDir)) {
                val classes = sibling.resolve("build/classes")
                if (Files.isDirectory(classes)) expanded.add(classes)
            }
        }
        return expanded
    }

    /** The module directory a path is inside of, or null for repo roots, jars, loose dirs. */
    private fun moduleDirOf(path: Path): Path? {
        val absolute = path.toAbsolutePath().normalize()
        val segments = absolute.toString().split('/', '\\')
        for (i in 1 until segments.size - 1) {
            val seg = segments[i].lowercase()
            val next = segments[i + 1].lowercase()
            val insideOutput =
                (seg == "build" && next == "classes") ||
                    (seg == "target" && (next == "classes" || next == "test-classes"))
            if (insideOutput) {
                // Keep components 1..i-1 (e.g. /tmp/proj/modB out of /tmp/proj/modB/build/classes/main).
                var dir = absolute
                repeat(segments.size - i) { dir = dir.parent }
                return dir
            }
        }
        return null
    }

    /** [projectDir] itself plus its direct subdirectories, all normalized. */
    private fun projectDirectories(projectDir: Path): List<Path> {
        val dirs = mutableListOf(projectDir.toAbsolutePath().normalize())
        Files.newDirectoryStream(projectDir).use { stream ->
            for (entry in stream) {
                if (Files.isDirectory(entry)) dirs.add(entry.toAbsolutePath().normalize())
            }
        }
        return dirs.sorted()
    }
}
