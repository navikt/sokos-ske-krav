package no.nav.sokos.ske.krav.util.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

import no.nav.sokos.ske.krav.config.CircuitBreakerManager.guardCall
import no.nav.sokos.ske.krav.config.CircuitBreakerPlugin
import no.nav.sokos.ske.krav.config.jsonConfig

enum class Endpoint(
    val url: String,
) {
    MOTTAKSSTATUS("/mottaksstatus"),
    OPPRETT("/innkrevingsoppdrag"),
    ENDRE_RENTER("/renter"),
    ENDRE_HOVEDSTOL("/hovedstol"),
    AVSKRIVING("/avskriving"),
    AVSTEMMING("/avstemming"),
    HENT_VALIDERINGSFEIL("/valideringsfeil"),
}

data class MockResponse(
    val originEndpoint: Endpoint,
    val content: String,
    val statusCode: HttpStatusCode = HttpStatusCode.OK,
)

object MockHttpClient {
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    val slackClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(jsonConfig) }

            engine {
                addHandler {
                    respond("", HttpStatusCode.OK, responseHeaders)
                }
            }
        }

    private fun mockEngine(mockResponses: List<MockResponse>) =
        MockEngine({ request ->
            val response = mockResponses.find { mockRequest -> matchesEndpoint(request.url.encodedPath, mockRequest.originEndpoint.url) }
            if (response != null) {
                respond(response.content, response.statusCode, responseHeaders)
            } else {
                error("Ikke implementert: ${request.url.encodedPath}")
            }
        })

    fun client(vararg mockResponses: MockResponse) =
        HttpClient(mockEngine(mockResponses.toList())) {
            install(ContentNegotiation) { json(jsonConfig) }
            install(CircuitBreakerPlugin)
        }.apply {
            plugin(HttpSend).intercept {
                guardCall { execute(it) }
            }
        }

    private fun matchesEndpoint(
        requestPath: String,
        endpointUrl: String,
    ) = requestPath.startsWith("/innkrevingsoppdrag") && requestPath.endsWith(endpointUrl)
}
