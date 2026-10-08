package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.stream.Collectors
import kotlin.io.path.extension

/**
 * Expands user-supplied paths into raw class-file bytes.
 *
 * Streaming: entries are produced lazily and each class payload is read and released
 * one at a time, so a fat jar never has to fit into the heap as a whole. Entry names
 * and paths are still materialized up front to keep the output deterministic.
 */
class ClassFileProvider(private val paths: List<Path>) {

    class Entry(val origin: String, val bytes: ByteArray)

    /**
     * Deterministic: paths in given order; directory walks and jar entries sorted by name.
     *
     * The sequence must be consumed fully: abandoning it midway keeps the current jar
     * handle open until GC (the analyzer always iterates to the end).
     */
    fun entries(): Sequence<Entry> = paths.asSequence().flatMap(::expand)

    private fun expand(path: Path): Sequence<Entry> = when {
        Files.isRegularFile(path) -> fileEntries(path)
        Files.isDirectory(path) -> dirEntries(path)
        else -> emptySequence()
    }

    private fun fileEntries(file: Path): Sequence<Entry> = when (file.extension.lowercase()) {
        "class" ->
            if (isSpecial(file)) emptySequence()
            else sequenceOf(Entry(file.toString(), Files.readAllBytes(file)))
        "jar", "zip" -> jarEntries(file)
        else -> emptySequence()
    }

    private fun dirEntries(dir: Path): Sequence<Entry> {
        // Collect paths only — payload bytes stay unread until the sequence is consumed.
        val files = Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .sorted()
                .collect(Collectors.toList())
        }
        return files.asSequence().flatMap(::fileEntries)
    }

    private fun jarEntries(jar: Path): Sequence<Entry> = sequence {
        JarFile(jar.toFile()).use { jf ->
            val names = jf.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.endsWith(".class") }
                .filterNot { it.name.startsWith("META-INF/versions/") }
                .filterNot { isSpecialJarName(it.name) }
                .map { it.name }
                .sorted()
                .toList()
            for (name in names) {
                yield(Entry("${jar}!/$name", jf.getInputStream(jf.getEntry(name)).readBytes()))
            }
        }
    }

    /** module-info / package-info carry no interesting structure. */
    private fun isSpecial(file: Path): Boolean = isSpecialJarName(file.toString())

    private fun isSpecialJarName(name: String): Boolean {
        val simple = name.substringAfterLast('/')
        return simple == "module-info.class" || simple == "package-info.class"
    }
}
