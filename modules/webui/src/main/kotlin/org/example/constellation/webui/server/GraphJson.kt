package org.example.constellation.webui.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.example.constellation.core.api.model.ClassOrigin
import org.example.constellation.core.api.model.CodeGraph
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.MethodSignature
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.Relation
import org.example.constellation.core.api.traits.ClassTrait
import org.example.constellation.core.api.traits.DataModel

/** Pure JSON rendering of the graph and its meta information. */
object GraphJson {

    private val json = JsonNodeFactory.instance
    private val mapper = ObjectMapper()

    /** Provider display names, when the providers are on this classpath. */
    private val displayNames: Map<String, String> by lazy {
        runCatching {
            org.example.constellation.core.api.traits.TraitRegistry.discover()
                .all().associate { it.id.value to it.displayName }
        }.getOrDefault(emptyMap())
    }

    fun graph(g: CodeGraph): String = mapper.writeValueAsString(
        obj {
            putArray("nodes").addAll(g.nodes.map(::nodeJson))
            putArray("edges").addAll(g.edges.map(::edgeJson))
        },
    )

    fun meta(g: CodeGraph): String = mapper.writeValueAsString(
        obj {
            putArray("sources").addAll(g.stats.sources.map(json::textNode))
            put("analyzedClasses", g.stats.analyzedClasses)
            put("skippedSynthetic", g.stats.skippedSynthetic)
            put("duplicatesSkipped", g.stats.duplicatesSkipped)
            putArray("parseErrors").addAll(
                g.stats.parseErrors.map { err ->
                    obj {
                        put("origin", err.origin)
                        put("message", err.message)
                    }
                },
            )
            putObject("kinds").putCounts(Categories.entries.map { it.id }) { cat ->
                g.nodes.count { Categories.of(it) == cat }
            }
            putObject("edgeTypes").putCounts(Relation.entries.map { it.name }) { rel ->
                g.edges.count { it.type.name == rel }
            }
            // Providers known to this graph — UIs can render unknown ones generically.
            putArray("traitCatalog").addAll(
                g.nodes.flatMap { it.traits.map { t -> t.id.value } }
                    .toSortedSet()
                    .map { id ->
                        obj {
                            put("id", id)
                            put("displayName", displayNames[id] ?: id.substringAfterLast('.'))
                            put("count", g.nodes.count { n -> n.traits.any { it.id.value == id } })
                        }
                    },
            )
            // Analyzed packages only; externals would dump half of the JDK here.
            putArray("packages").addAll(
                g.nodes.filter { !it.isExternal }
                    .groupingBy { it.pkg }
                    .eachCount()
                    .toSortedMap()
                    .map { (pkg, count) ->
                        obj {
                            put("name", pkg)
                            put("count", count)
                        }
                    },
            )
        },
    )

    // ---------- node/edge payloads ----------

    private fun nodeJson(n: Node): JsonNode = obj {
        put("id", n.id)
        put("kind", n.kind.name)
        put("category", Categories.of(n))
        put("pkg", n.pkg)
        put("simpleName", n.simpleName)
        n.sourceFile?.let { put("sourceFile", it) }
        putObject("origin").apply {
            when (val origin = n.origin) {
                is ClassOrigin.Project -> {
                    put("type", "project")
                    put("module", origin.module)
                    put("sourceSet", origin.sourceSet)
                }
                ClassOrigin.External -> put("type", "external")
            }
        }
        putArray("traits").addAll(n.traits.map(::traitJson))
        putArray("methods").addAll(
            n.declaredMethods.map { m ->
                obj {
                    put("name", m.name)
                    putArray("params").addAll(m.params.map { json.textNode(it.render()) })
                    put("display", m.render())
                }
            },
        )
    }

    private fun traitJson(t: ClassTrait): JsonNode = obj {
        put("id", t.id.value)
        when (t) {
            is DataModel -> {
                put("carrier", t.carrier.name)
                putArray("components").addAll(t.components.map(json::textNode))
            }
            is org.example.constellation.core.api.traits.StaticComponent -> put("carrier", t.carrier.name)
        }
    }

    private fun edgeJson(e: Edge): JsonNode = obj {
        put("from", e.from)
        put("to", e.to)
        put("type", e.type.name)
        if (e.type == Relation.CALLS || e.type == Relation.CALLS_STATIC) {
            putArray("methods").addAll(e.methods.map(::methodRefJson))
        }
    }

    private fun methodRefJson(m: MethodSignature): JsonNode = obj {
        put("name", m.name)
        put("display", m.render())
    }

    // ---------- small builders ----------

    private inline fun obj(block: ObjectNode.() -> Unit): ObjectNode =
        json.objectNode().apply(block)

    private fun ObjectNode.putCounts(values: Collection<String>, count: (String) -> Int) {
        for (v in values) put(v, count(v))
    }
}
