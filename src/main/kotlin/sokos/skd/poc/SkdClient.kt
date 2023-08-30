package sokos.skd.poc

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*

import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient

private const val OPPRETT_KRAV = "innkrevingsoppdrag"
private const val ENDRE_KRAV = "innkrevingsoppdrag/endring"
private const val STOPP_KRAV = "innkrevingsoppdrag/avskriv"

class SkdClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skdEndpoint: String,
    private val engine: HttpClientEngine = CIO.create(),
    private val client: HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(Logging){ level = LogLevel.INFO}
    },

    ) {
    suspend fun opprettKrav(body: String): HttpResponse = doPost(OPPRETT_KRAV, body)
    suspend fun endreKrav(body: String): HttpResponse = doPost(ENDRE_KRAV, body)
    suspend fun stoppKrav(body: String):HttpResponse = doPost(STOPP_KRAV, body)

    @OptIn(InternalAPI::class)
    private suspend fun doPost(path: String, body: String): HttpResponse {
        val token = tokenProvider.hentAccessToken()
        val response = client.post("$skdEndpoint$path") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("Klientid", "NAV/0.1")
                }
                setBody(body)
            }
            println("resp_body: ${response.bodyAsText()}, \n${response.headers}, \n${response.content}, \n${response.request.call}")

        return response
    }
}


