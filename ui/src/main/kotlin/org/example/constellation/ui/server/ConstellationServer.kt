package org.example.constellation.ui.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.example.constellation.core.filter.GraphFilter
import org.example.constellation.core.model.CodeGraph
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors

/** Serves the static page and the graph JSON API on the loopback interface. */
class ConstellationServer(private val graph: CodeGraph, port: Int = 0) {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    private val executor = Executors.newFixedThreadPool(4)

    fun start(): URI {
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        return URI("http://127.0.0.1:${server.address.port}/")
    }

    fun stop() {
        server.stop(0)
        executor.shutdown()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            exchange.requestBody.close()
            val path = exchange.requestURI.path ?: "/"
            when {
                path == "/api/graph" -> handleGraph(exchange)
                path == "/api/meta" -> respond(exchange, 200, "application/json", GraphJson.meta(graph))
                path.startsWith("/vendor/") -> static(exchange, path)
                path == "/" || path.startsWith("/web/") || !path.contains("..") ->
                    static(exchange, if (path == "/") "/web/index.html" else "/web$path")
                else -> respond(exchange, 404, "text/plain", "not found")
            }
        } catch (e: IllegalArgumentException) {
            respond(exchange, 400, "text/plain", e.message ?: "bad request")
        } catch (e: Exception) {
            respond(exchange, 500, "text/plain", e.toString())
        } finally {
            exchange.close()
        }
    }

    private fun handleGraph(exchange: HttpExchange) {
        val spec = GraphQuery.parse(exchange.requestURI.rawQuery)
        val filtered = GraphFilter.apply(graph, spec)
        respond(exchange, 200, "application/json", GraphJson.graph(filtered))
    }

    private fun static(exchange: HttpExchange, resourcePath: String) {
        if (resourcePath.contains("..")) {
            respond(exchange, 404, "text/plain", "not found")
            return
        }
        val bytes = javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes() }
        if (bytes == null) {
            respond(exchange, 404, "text/plain", "not found")
            return
        }
        respond(exchange, 200, mimeOf(resourcePath), bytes.toString(Charsets.UTF_8), bytes)
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, text: String, raw: ByteArray? = null) {
        val body = raw ?: text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "$contentType; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1L else body.size.toLong())
        if (body.isNotEmpty()) {
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js" -> "text/javascript"
        "css" -> "text/css"
        "json", "map" -> "application/json"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}
