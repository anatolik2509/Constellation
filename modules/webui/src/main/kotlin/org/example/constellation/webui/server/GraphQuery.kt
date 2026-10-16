package org.example.constellation.webui.server

import org.example.constellation.core.model.Relation
import org.example.constellation.core.properties.PropertyId
import org.example.constellation.core.query.EdgePredicate
import org.example.constellation.core.query.EdgePredicates
import org.example.constellation.core.query.NodePredicate
import org.example.constellation.core.query.NodePredicates
import org.example.constellation.core.query.and
import org.example.constellation.core.query.not
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure query parsing — kept free of HTTP types so it is trivially testable.
 * Composes core predicates; unknown tokens throw [IllegalArgumentException]
 * (mapped to HTTP 400 by the server).
 */
object GraphQuery {

    fun parse(query: String?): Pair<NodePredicate, EdgePredicate> {
        if (query.isNullOrBlank()) return NodePredicates.all() to EdgePredicates.all()

        var categories: Set<String>? = null
        var relations: Set<Relation>? = null
        var packages: List<String>? = null
        var excludedPackages: List<String>? = null
        var excludedProperties: List<PropertyId>? = null

        for (raw in query.split('&')) {
            val eq = raw.indexOf('=')
            if (eq <= 0) continue
            val name = URLDecoder.decode(raw.substring(0, eq), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(raw.substring(eq + 1), StandardCharsets.UTF_8)
            when (name) {
                "kinds" -> categories = value.split(',').filter { it.isNotBlank() }
                    .map { categoryById(it.trim()) }.toSet().takeIf { it.isNotEmpty() }
                "edges" -> relations = value.split(',').filter { it.isNotBlank() }
                    .map { Relation.valueOf(it.trim()) }.toSet().takeIf { it.isNotEmpty() }
                "pkgs" -> packages = value.split(',').map { it.trim() }
                    .filter { it.isNotEmpty() }.map { wirePackage(it) }
                    .takeIf { it.isNotEmpty() }
                "xpkgs" -> excludedPackages = value.split(',').map { it.trim() }
                    .filter { it.isNotEmpty() }.map { wirePackage(it) }
                    .takeIf { it.isNotEmpty() }
                "xprops" -> excludedProperties = value.split(',').map { it.trim() }
                    .filter { it.isNotEmpty() }.map { PropertyId(it) }
                    .takeIf { it.isNotEmpty() }
            }
        }

        var node = Categories.predicateFor(categories ?: Categories.entries.map { it.id }) ?: NodePredicates.all()
        if (packages != null) {
            node = node and NodePredicates.inPackage(*packages.toTypedArray())
        }
        if (excludedPackages != null) {
            node = node and NodePredicates.notInPackage(*excludedPackages.toTypedArray())
        }
        if (excludedProperties != null) {
            node = node and !NodePredicates.hasAnyProperty(excludedProperties)
        }
        return node to (relations?.let { EdgePredicates.types(*it.toTypedArray()) } ?: EdgePredicates.all())
    }

    private fun categoryById(token: String): String {
        if (Categories.entries.none { it.id == token }) {
            throw IllegalArgumentException(
                "unknown category '$token' (expected one of ${Categories.entries.map { it.id }})",
            )
        }
        return token
    }

    /** The default package travels as the sentinel `(default)` — an empty token would be dropped. */
    private fun wirePackage(token: String): String =
        if (token == "(default)") "" else token
}
