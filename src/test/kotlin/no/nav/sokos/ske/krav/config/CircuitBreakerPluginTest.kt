package no.nav.sokos.ske.krav.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.util.MockHttpClient

class CircuitBreakerPluginTest :
    FunSpec({

        val mockHttpClient = MockHttpClient()
        beforeEach {
            circuitBreaker.reset()
        }

        test("circuit breaker should remain closed on successful requests") {
            var requestCount = 0

            val mockEngine =
                MockEngine { _ ->
                    requestCount++
                    respond(
                        content = """{"status":"ok"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)

            repeat(3) {
                client.get("https://example.com/api")
            }

            requestCount shouldBe 3
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            client.close()
        }

        test("circuit breaker should open on first HTTP 401 Unauthorized") {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"Unauthorized"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/api")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should open on first HTTP 403 Forbidden") {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"Forbidden"}""",
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)
            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/api")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should open on first HTTP 5xx") {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"Internal Server Error"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/api")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should not open on HTTP 404 Not Found") {
            var requestCount = 0

            val mockEngine =
                MockEngine { _ ->
                    requestCount++
                    respond(
                        content = """{"error":"Not Found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)

            repeat(3) {
                client.get("https://example.com/api")
            }

            requestCount shouldBe 3
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            client.close()
        }

        test("circuit breaker should fail after response when OPEN") {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"Service Unavailable"}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client = mockHttpClient.guardedClient(mockEngine)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/api")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }
    })
