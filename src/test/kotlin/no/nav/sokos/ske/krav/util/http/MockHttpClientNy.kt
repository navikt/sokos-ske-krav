package no.nav.sokos.ske.krav.util.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

import no.nav.sokos.ske.krav.config.jsonConfig

object MockHttpClientNy {
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
}
