package org.example.constellation.plugin

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end: a fixture project applies the plugin, `./gradlew constellation`
 * compiles, analyzes and serves the UI — which is then queried over HTTP.
 */
class ConstellationIntegrationTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `constellation task compiles, analyzes and serves the graph`() {
        // A free ephemeral port (reserved then released) keeps concurrent builds from colliding.
        val port = java.net.ServerSocket(0).use { it.localPort }
        val projectDir = tmp.resolve("demo")
        Files.createDirectories(projectDir.resolve("src/main/java/demo"))
        Files.writeString(
            projectDir.resolve("settings.gradle.kts"),
            """
            rootProject.name = "demo"
            """.trimIndent(),
        )
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                java
                id("org.example.constellation")
            }
            constellation {
                port = $port
                openBrowser = false
                autoStopSeconds = 25
            }
            """.trimIndent(),
        )
        Files.writeString(
            projectDir.resolve("src/main/java/demo/Hello.java"),
            """
            package demo;

            public class Hello {
                public String greet() { return "hi"; }
            }
            """.trimIndent(),
        )

        val done = CountDownLatch(1)
        var buildOutput = ""
        val runner = Thread {
            buildOutput = runCatching {
                GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments("constellation")
                    .withPluginClasspath()
                    .build()
                    .output
            }.getOrElse { e -> "BUILD FAILED: ${e.message}" }
            done.countDown()
        }
        runner.start()

        // The task compiles first, then serves until autoStop — poll the API meanwhile.
        val meta = try {
            awaitSuccess("http://127.0.0.1:$port/api/meta")
        } catch (e: AssertionError) {
            done.await(90, TimeUnit.SECONDS)
            throw AssertionError("${e.message}\n--- gradle output ---\n$buildOutput", e)
        }
        assertTrue(meta.contains("\"analyzedClasses\":1"), meta)

        val graph = awaitSuccess("http://127.0.0.1:$port/api/graph")
        assertTrue(graph.contains("\"id\":\"demo.Hello\""), graph)
        assertTrue(graph.contains("\"module\":\"demo\""), graph)

        // Let autoStop end the build; assert the task reported the server URL.
        assertTrue(done.await(60, TimeUnit.SECONDS), "gradle build did not finish")
        assertTrue(buildOutput.contains("constellation running at http://127.0.0.1:$port"), buildOutput)
    }

    private fun awaitSuccess(url: String): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        var lastError = ""
        while (System.nanoTime() < deadline) {
            try {
                val response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                if (response.statusCode() == 200) return response.body()
                lastError = "HTTP ${response.statusCode()}"
            } catch (e: Exception) {
                lastError = e.toString()
            }
            Thread.sleep(500)
        }
        throw AssertionError("server did not come up at $url: $lastError")
    }
}
