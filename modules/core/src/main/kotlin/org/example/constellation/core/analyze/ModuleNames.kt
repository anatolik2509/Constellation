package org.example.constellation.core.analyze

/**
 * Best-effort module name for a class file, from where it came from:
 * Gradle/Maven layouts (`<module>/build/classes/…`, `<module>/src/…`) or a jar name
 * with the version suffix stripped (`kotlin-stdlib-2.4.20.jar` -> `kotlin-stdlib`).
 */
internal object ModuleNames {

    private val VERSION_SUFFIX = Regex("-\\d+(\\.\\d+)*$")

    fun of(origin: String): String? {
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
}
