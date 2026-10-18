package org.example.constellation.webui.server

import org.example.constellation.core.api.model.ClassKind
import org.example.constellation.core.api.model.ClassOrigin
import org.example.constellation.core.api.model.CodeGraph
import org.example.constellation.core.api.model.Edge
import org.example.constellation.core.api.model.GraphStats
import org.example.constellation.core.api.model.Node
import org.example.constellation.core.api.model.Relation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ConstellationServerSmokeTest {

    private val graph: CodeGraph = CodeGraph(
        nodes = listOf(
            node("demo.Foo", ClassOrigin.Project("app", "main")),
            node("demo.Bar", ClassOrigin.Project("app", "main")),
            node("demo.BarTest", ClassOrigin.Project("app", "test")),
        ),
        edges = listOf(Edge("demo.Foo", "demo.Bar", Relation.CALLS)),
        stats = GraphStats(3, 0, 0, emptyList(), emptyList()),
    )

    private fun node(id: String, origin: ClassOrigin) = Node(
        id = id, kind = ClassKind.CLASS, origin = origin,
        pkg = id.substringBeforeLast('.', ""), simpleName = id.substringAfterLast('.'),
        sourceFile = null, declaredMethods = emptyList(), traits = emptyList(),
    )

    @Test
    fun `serves page, api and filters, and rejects bad queries`() {
        // Ephemeral port: the default 8080 may be taken by anything on the machine.
        val server = ConstellationServer(graph, 0)
        val uri = server.start()
        try {
            HttpClient.newHttpClient().use { client ->
                fun get(path: String): HttpResponse<String> =
                    client.send(
                        HttpRequest.newBuilder(uri.resolve(path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )

                val page = get("/")
                assertEquals(200, page.statusCode())
                assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"))

                val api = get("/api/graph")
                assertEquals(200, api.statusCode())
                assertTrue(api.body().contains("\"id\":\"demo.Foo\""))

                val filtered = get("/api/graph?kinds=CLASS&edges=&pkgs=demo")
                assertEquals(200, filtered.statusCode())
                assertTrue(filtered.body().contains("demo.Bar"))

                val trimmedCalls = get("/api/graph?xcalltraits=CONSTRUCTOR")
                assertEquals(200, trimmedCalls.statusCode())

                val noTests = get("/api/graph?kinds=CLASS,INTERFACE,ANNOTATION,DATA,EXTERNAL")
                assertEquals(200, noTests.statusCode())
                assertTrue(!noTests.body().contains("demo.BarTest"))

                val meta = get("/api/meta")
                assertEquals(200, meta.statusCode())
                assertTrue(meta.body().contains("\"CALLS\":1"))

                val bad = get("/api/graph?kinds=BOGUS")
                assertEquals(400, bad.statusCode())

                assertEquals(404, get("/no/such/file.js").statusCode())
            }
        } finally {
            server.stop()
        }
    }
}
