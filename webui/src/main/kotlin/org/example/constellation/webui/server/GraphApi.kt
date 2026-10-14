package org.example.constellation.webui.server

import org.example.constellation.core.filter.FilterSpec
import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.util.Descriptors
import org.example.constellation.webui.json.JsonWriter
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

    fun graph(g: CodeGraph): String = JsonWriter().obj {
        key("nodes")
        arr {
            for (n in g.nodes) {
                obj {
                    key("id"); str(n.id)
                    key("kind"); str(n.kind.name)
                    key("category"); str(n.category.name)
                    key("pojo"); bool(n.pojo)
                    key("pkg"); str(n.pkg)
                    key("simpleName"); str(n.simpleName)
                    val sf = n.sourceFile
                    if (sf != null) {
                        key("sourceFile"); str(sf)
                    }
                    key("methods")
                    arr {
                        for (m in n.declaredMethods) {
                            obj {
                                key("name"); str(m.name)
                                key("desc"); str(m.descriptor)
                                key("display"); str(Descriptors.formatMethod(m.name, m.descriptor))
                            }
                        }
                    }
                }
            }
        }
        key("edges")
        arr {
            for (e in g.edges) {
                obj {
                    key("from"); str(e.from)
                    key("to"); str(e.to)
                    key("type"); str(e.type.name)
                    if (e.type == EdgeType.CALL) {
                        key("methods")
                        arr {
                            for (m in e.methods) {
                                obj {
                                    key("name"); str(m.name)
                                    key("display"); str(Descriptors.formatMethod(m.name, m.descriptor))
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString()

    fun meta(g: CodeGraph): String = JsonWriter().obj {
        key("sources")
        arr {
            for (s in g.stats.sources) str(s)
        }
        key("analyzedClasses"); int(g.stats.analyzedClasses)
        key("skippedSynthetic"); int(g.stats.skippedSynthetic)
        key("duplicatesSkipped"); int(g.stats.duplicatesSkipped)
        key("parseErrors")
        arr {
            for (e in g.stats.parseErrors) {
                obj {
                    key("origin"); str(e.origin)
                    key("message"); str(e.message)
                }
            }
        }

        key("kinds")
        obj {
            for (cat in NodeCategory.entries) {
                key(cat.name); int(g.nodes.count { it.category == cat })
            }
        }
        key("edgeTypes")
        obj {
            for (t in EdgeType.entries) {
                key(t.name); int(g.edges.count { it.type == t })
            }
        }
        // Analyzed packages only; externals would dump half of the JDK here.
        key("packages")
        arr {
            for ((pkg, count) in g.nodes
                .filter { it.kind != org.example.constellation.core.model.NodeKind.EXTERNAL }
                .groupBy { it.pkg }
                .map { (pkg, ns) -> pkg to ns.size }
                .sortedBy { it.first }) {
                obj {
                    key("name"); str(pkg)
                    key("count"); int(count)
                }
            }
        }
    }.toString()
}
