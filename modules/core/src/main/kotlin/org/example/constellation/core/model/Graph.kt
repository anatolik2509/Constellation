package org.example.constellation.core.model

/** Structural kind of a node as read from bytecode access flags. */
enum class NodeKind { CLASS, INTERFACE, ANNOTATION, EXTERNAL }

/**
 * Effective category used for filtering and coloring.
 * POJO is a refinement of CLASS: a class detected as POJO has category POJO, not CLASS.
 */
enum class NodeCategory { CLASS, POJO, INTERFACE, ANNOTATION, EXTERNAL }

enum class EdgeType { CALL, INHERITANCE, IMPLEMENTS }

/** A JVM method reference; [descriptor] is the raw JVM form, e.g. `()V`. */
data class MethodRef(val name: String, val descriptor: String)

data class Node(
    /** Dot-separated FQN, e.g. `com.foo.Bar` (nested classes keep `$`). */
    val id: String,
    val kind: NodeKind,
    /** Only meaningful for [NodeKind.CLASS]. */
    val pojo: Boolean = false,
    /** Package part of [id]; empty string for the default package. */
    val pkg: String,
    /** Last `$`- or `.`-segment of [id]. */
    val simpleName: String,
    /** SourceFile attribute; null for externals. */
    val sourceFile: String? = null,
    /** Declared non-synthetic methods, sorted; empty for externals. */
    val declaredMethods: List<MethodRef> = emptyList(),
) {
    val category: NodeCategory
        get() = when {
            kind == NodeKind.EXTERNAL -> NodeCategory.EXTERNAL
            pojo -> NodeCategory.POJO
            kind == NodeKind.CLASS -> NodeCategory.CLASS
            kind == NodeKind.INTERFACE -> NodeCategory.INTERFACE
            else -> NodeCategory.ANNOTATION
        }
}

data class Edge(
    val from: String,
    val to: String,
    val type: EdgeType,
    /** Called methods; present on [EdgeType.CALL] edges only. */
    val methods: List<MethodRef> = emptyList(),
)

/** A class file the analyzer failed to parse; [origin] is the jar/path it came from. */
data class ParseError(val origin: String, val message: String)

data class GraphStats(
    val analyzedClasses: Int,
    val skippedSynthetic: Int,
    val duplicatesSkipped: Int,
    val parseErrors: List<ParseError>,
    val sources: List<String>,
)

/** Fully sorted by construction, so JSON output is deterministic. */
data class CodeGraph(
    val nodes: List<Node>,
    val edges: List<Edge>,
    val stats: GraphStats,
)
