package org.example.constellation.core.api

import org.example.constellation.core.api.traits.TraitRegistry

/** What to analyze: class roots plus the trait providers to run. */
data class AnalysisRequest(
    val roots: List<ClassRoot>,
    val registry: TraitRegistry = TraitRegistry.discover(),
)
