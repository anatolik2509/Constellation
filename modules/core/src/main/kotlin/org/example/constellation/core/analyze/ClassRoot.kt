package org.example.constellation.core.analyze

import org.example.constellation.core.traits.TraitRegistry
import java.nio.file.Path

/**
 * A directory of compiled classes together with what it represents:
 * the module and source set it belongs to. Labels come from the caller
 * (the Gradle plugin knows them), never from path sniffing.
 */
data class ClassRoot(val dir: Path, val module: String, val sourceSet: String)

/** What to analyze: class roots plus the trait providers to run. */
data class AnalysisRequest(
    val roots: List<ClassRoot>,
    val registry: TraitRegistry = TraitRegistry.discover(),
)
