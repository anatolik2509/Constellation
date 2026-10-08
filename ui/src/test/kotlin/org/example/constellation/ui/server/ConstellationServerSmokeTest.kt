package org.example.constellation.ui.server

import org.example.constellation.core.model.CodeGraph
import org.example.constellation.core.model.Edge
import org.example.constellation.core.model.EdgeType
import org.example.constellation.core.model.GraphStats
import org.example.constellation.core.model.Node
import org.example.constellation.core.model.NodeCategory
import org.example.constellation.core.model.NodeKind
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
            Node("demo.Foo", NodeKind.CLASS, pkg = "demo", simpleName = "Foo"),
            Node("demo.Bar", NodeKind.CLASS, pkg = "demo", simpleName = "Bar"),
        ),
        edges = listOf(Edge("demo.Foo", "demo.Bar", EdgeType.CALL)),
        stats = GraphStats(2, 0, 0, emptyList(), emptyList()),
    )

    @Test
    fun `serves page, api and filters, and rejects bad queries`() {
        val server = ConstellationServer(graph)
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

                val meta = get("/api/meta")
                assertEquals(200, meta.statusCode())
                assertTrue(meta.body().contains("\"CALL\":1"))

                val bad = get("/api/graph?kinds=BOGUS")
                assertEquals(400, bad.statusCode())

                assertEquals(404, get("/no/such/file.js").statusCode())
            }
        } finally {
            server.stop()
        }
    }
}
