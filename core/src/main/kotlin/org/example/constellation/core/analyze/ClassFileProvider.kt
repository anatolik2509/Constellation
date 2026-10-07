package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.stream.Collectors
import kotlin.io.path.extension

/** Expands user-supplied paths into raw class-file bytes. */
class ClassFileProvider(private val paths: List<Path>) {

    class Entry(val origin: String, val bytes: ByteArray)

    /** Deterministic: paths in given order; directory walks and jar entries sorted by name. */
    fun entries(): List<Entry> = paths.flatMap(::expand)

    private fun expand(path: Path): List<Entry> {
        return when {
            Files.isRegularFile(path) -> when (path.extension.lowercase()) {
                "class" -> if (isSpecial(path)) emptyList() else listOf(Entry(path.toString(), Files.readAllBytes(path)))
                "jar", "zip" -> jarEntries(path)
                else -> emptyList()
            }
            Files.isDirectory(path) -> dirEntries(path)
            else -> emptyList()
        }
    }

    private fun dirEntries(dir: Path): List<Entry> {
        val files = Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .sorted()
                .collect(Collectors.toList())
        }
        return files.flatMap { file ->
            when (file.extension.lowercase()) {
                "class" ->
                    if (isSpecial(file)) emptyList()
                    else listOf(Entry(file.toString(), Files.readAllBytes(file)))
                "jar", "zip" -> jarEntries(file)
                else -> emptyList()
            }
        }
    }

    private fun jarEntries(jar: Path): List<Entry> =
        JarFile(jar.toFile()).use { jf ->
            jf.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.endsWith(".class") }
                .filterNot { it.name.startsWith("META-INF/versions/") }
                .filterNot { isSpecialJarName(it.name) }
                .sortedBy { it.name }
                .map { Entry("${jar}!/${it.name}", jf.getInputStream(it).readBytes()) }
                .toList()
        }

    /** module-info / package-info carry no interesting structure. */
    private fun isSpecial(file: Path): Boolean = isSpecialJarName(file.toString())

    private fun isSpecialJarName(name: String): Boolean {
        val simple = name.substringAfterLast('/')
        return simple == "module-info.class" || simple == "package-info.class"
    }
}
