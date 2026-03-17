package no.nav.sokos.ske.krav.util

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

object MockHttpClientUtils {
    enum class EndepunktType(
        val url: String,
    ) {
        MOTTAKSSTATUS("/mottaksstatus"),
        OPPRETT("/innkrevingsoppdrag"),
        ENDRE_RENTER("/renter"),
        ENDRE_HOVEDSTOL("/hovedstol"),
        AVSKRIVING("/avskriving"),
        HENT_VALIDERINGSFEIL("/valideringsfeil"),
    }

    data class MockRequestObj(
        val response: String,
        val type: EndepunktType,
        val statusCode: HttpStatusCode,
    )
}

class MockHttpClient {
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

    fun getSlackClient() =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(jsonConfig) }
            engine {
                addHandler {
                    respond("", HttpStatusCode.OK, responseHeaders)
                }
            }
        }

    fun guardedClient(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(jsonConfig) }
            install(CircuitBreakerPlugin)
        }.apply {
            plugin(HttpSend).intercept {
                guardCall { execute(it) }
            }
        }

    fun getClient(kall: List<MockHttpClientUtils.MockRequestObj>): HttpClient {
        val mockEngine =
            MockEngine { request ->
                val handler =
                    kall.singleOrNull {
                        generateUrls(it.type.url).contains(request.url.encodedPath)
                    }
                if (handler != null) {
                    respond(handler.response, handler.statusCode, responseHeaders)
                } else {
                    error("Ikke implementert: ${request.url.encodedPath}")
                }
            }

        return guardedClient(mockEngine)
    }

    private fun generateUrls(baseUrl: String) =
        listOf(
            "/innkrevingsoppdrag/foo$baseUrl",
            "/innkrevingsoppdrag/1234$baseUrl",
            "/innkrevingsoppdrag/OB040000592759$baseUrl",
            "/innkrevingsoppdrag/OB040000479803$baseUrl",
            "/innkrevingsoppdrag/OB040000595755$baseUrl",
            "/innkrevingsoppdrag/2220-navsaksnummer$baseUrl",
            "/innkrevingsoppdrag/1110-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1111-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1112-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1113-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1114-skeUUID$baseUrl",
            "/innkrevingsoppdrag/2222-skeUUID$baseUrl",
            "/innkrevingsoppdrag/3333-skeUUID$baseUrl",
            "/innkrevingsoppdrag/4444-skeUUID$baseUrl",
            "/innkrevingsoppdrag/5555-skeUUID$baseUrl",
            "/innkrevingsoppdrag/6666-skeUUID$baseUrl",
            "/innkrevingsoppdrag/7777-skeUUID$baseUrl",
            "/innkrevingsoppdrag/8888-skeUUID$baseUrl",
            "/innkrevingsoppdrag/9999-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1010-skeUUID$baseUrl",
            "/innkrevingsoppdrag/kravidske1$baseUrl",
            "/innkrevingsoppdrag/kravidske2$baseUrl",
            "/innkrevingsoppdrag/$baseUrl",
            "/innkrevingsoppdrag$baseUrl",
            baseUrl,
        )
}
