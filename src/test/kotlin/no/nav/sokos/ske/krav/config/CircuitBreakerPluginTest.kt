package no.nav.sokos.ske.krav.config

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject

import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse

class CircuitBreakerPluginTest :
    FunSpec({
        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        beforeEach {
            circuitBreaker.reset()
        }

        test("circuit breaker should remain closed on successful requests") {
            val mockResponse =
                MockResponse(
                    originEndpoint = Endpoint.MOTTAKSSTATUS,
                    content = """{"status":"ok"}""",
                )

            val client = MockHttpClient.client(mockResponse)

            repeat(3) {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            val requestCount = (client.engine as MockEngine).requestHistory.size
            requestCount shouldBe 3
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            client.close()
        }

        test("circuit breaker should open on first HTTP 401 Unauthorized") {
            val mockResponse =
                MockResponse(
                    originEndpoint = Endpoint.MOTTAKSSTATUS,
                    content = """{"error":"Unauthorized"}""",
                    statusCode = HttpStatusCode.Unauthorized,
                )

            val client = MockHttpClient.client(mockResponse)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should open on first HTTP 403 Forbidden") {
            val mockResponse =
                MockResponse(
                    Endpoint.MOTTAKSSTATUS,
                    content = """{"error":"Forbidden"}""",
                    statusCode = HttpStatusCode.Forbidden,
                )

            val client = MockHttpClient.client(mockResponse)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should open on first HTTP 5xx") {
            val mockResponse =
                MockResponse(
                    originEndpoint = Endpoint.MOTTAKSSTATUS,
                    content = """{"error":"Internal Server Error"}""",
                    statusCode = HttpStatusCode.InternalServerError,
                )

            val client = MockHttpClient.client(mockResponse)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            client.close()
        }

        test("circuit breaker should not open on HTTP 404 Not Found") {
            val mockResponse =
                MockResponse(
                    originEndpoint = Endpoint.MOTTAKSSTATUS,
                    content = """{"error":"Not Found"}""",
                    statusCode = HttpStatusCode.NotFound,
                )

            val client = MockHttpClient.client(mockResponse)

            repeat(3) {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            val requestCount = (client.engine as MockEngine).requestHistory.size
            requestCount shouldBe 3
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            client.close()
        }

        test("circuit breaker should fail after response when OPEN") {
            val mockResponse =
                MockResponse(
                    Endpoint.MOTTAKSSTATUS,
                    content = """{"error":"Service Unavailable"}""",
                    statusCode = HttpStatusCode.ServiceUnavailable,
                )

            val client = MockHttpClient.client(mockResponse)

            shouldThrow<CircuitBreakerException> {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN
            shouldThrow<CallNotPermittedException> {
                client.get("https://example.com/innkrevingsoppdrag/mottaksstatus")
            }

            client.close()
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }
    })
