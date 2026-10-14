package org.example.constellation.webui.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.example.constellation.core.filter.FilterSpec
import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.MethodRef
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind
import org.example.constellation.core.util.Descriptors
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Pure query parsing — kept free of HTTP types so it is trivially testable. */
object GraphQuery {

    /**
     * Parses `kinds=CLASS,POJO&edges=CALL&pkgs=com.foo,org.bar`.
     * A missing axis means "no restriction". Unknown enum tokens throw
     * [IllegalArgumentException] (mapped to HTTP 400 by the server).
     */
    fun parse(query: String?): FilterSpec {
        if (query.isNullOrBlank()) return FilterSpec()

        var kinds: Set<NodeCategory>? = null
        var edgeTypes: Set<EdgeType>? = null
        var packages: List<String>? = null

        for (raw in query.split('&')) {
            val eq = raw.indexOf('=')
            if (eq <= 0) continue
            val name = URLDecoder.decode(raw.substring(0, eq), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(raw.substring(eq + 1), StandardCharsets.UTF_8)
            when (name) {
                "kinds" -> kinds = value.split(',').filter { it.isNotBlank() }
                    .map { NodeCategory.valueOf(it.trim()) }.toSet().takeIf { it.isNotEmpty() }
                "edges" -> edgeTypes = value.split(',').filter { it.isNotBlank() }
                    .map { EdgeType.valueOf(it.trim()) }.toSet().takeIf { it.isNotEmpty() }
                "pkgs" -> packages = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
            }
        }

        return FilterSpec(
            kinds = kinds ?: NodeCategory.entries.toSet(),
            edgeTypes = edgeTypes ?: EdgeType.entries.toSet(),
            packages = packages ?: emptyList(),
        )
    }
}

/** Pure JSON rendering of the graph and its meta information. */
object GraphJson {

    private val json = JsonNodeFactory.instance
    private val mapper = ObjectMapper()

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
            putObject("kinds").putCounts(NodeCategory.entries) { cat -> g.nodes.count { it.category == cat } }
            putObject("edgeTypes").putCounts(EdgeType.entries) { type -> g.edges.count { it.type == type } }
            // Analyzed packages only; externals would dump half of the JDK here.
            putArray("packages").addAll(
                g.nodes.filter { it.kind != NodeKind.EXTERNAL }
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
        put("category", n.category.name)
        put("pojo", n.pojo)
        put("pkg", n.pkg)
        put("simpleName", n.simpleName)
        n.sourceFile?.let { put("sourceFile", it) }
        putArray("methods").addAll(
            n.declaredMethods.map { m ->
                obj {
                    put("name", m.name)
                    put("desc", m.descriptor)
                    put("display", Descriptors.formatMethod(m.name, m.descriptor))
                }
            },
        )
    }

    private fun edgeJson(e: Edge): JsonNode = obj {
        put("from", e.from)
        put("to", e.to)
        put("type", e.type.name)
        if (e.type == EdgeType.CALL) {
            putArray("methods").addAll(e.methods.map(::methodRefJson))
        }
    }

    private fun methodRefJson(m: MethodRef): JsonNode = obj {
        put("name", m.name)
        put("display", Descriptors.formatMethod(m.name, m.descriptor))
    }

    // ---------- small builders ----------

    private inline fun obj(block: ObjectNode.() -> Unit): ObjectNode =
        json.objectNode().apply(block)

    /** Fills an object with `<enumName>: count` entries for every [values] element. */
    private fun <T : Enum<T>> ObjectNode.putCounts(values: Collection<T>, count: (T) -> Int) {
        for (v in values) put(v.name, count(v))
    }
}
