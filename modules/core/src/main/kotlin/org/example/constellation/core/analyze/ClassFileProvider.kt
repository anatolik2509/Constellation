package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Feeds class files from the analysis roots. Directories only: the tool targets
 * projects with sources, where compiled output lives in directories — running on
 * jars is not a supported case.
 */
class ClassFileProvider(private val roots: List<ClassRoot>) {

    class Entry(val root: ClassRoot, val origin: String, val bytes: ByteArray)

    /** Deterministic: roots in given order; directory walks sorted by path. */
    fun entries(): List<Entry> = roots.flatMap(::expand)

    private fun expand(root: ClassRoot): List<Entry> {
        if (!Files.isDirectory(root.dir)) return emptyList()
        val files = Files.walk(root.dir).use { stream ->
            stream.filter { file ->
                Files.isRegularFile(file) &&
                    file.toString().endsWith(".class") &&
                    !isSpecial(file)
            }
                .sorted()
                .collect(Collectors.toList())
        }
        return files.map { file ->
            Entry(root, file.toString(), Files.readAllBytes(file))
        }
    }

    /** module-info (ACC_MODULE, not synthetic) and package-info carry no interesting structure. */
    private fun isSpecial(file: Path): Boolean {
        val name = file.fileName.toString()
        return name == "module-info.class" || name == "package-info.class"
    }
}
