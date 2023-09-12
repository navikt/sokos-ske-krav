package sokos.ske.krav.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.skemodels.requests.AvskrivingRequest
import sokos.ske.krav.skemodels.requests.EndringRequest
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest

private const val OPPRETT_KRAV = "innkrevingsoppdrag"
private const val ENDRE_KRAV = "innkrevingsoppdrag/endring"
private const val STOPP_KRAV = "innkrevingsoppdrag/avskriv"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val engine: HttpClientEngine = CIO.create(),
    private val client: HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(Logging){ level = LogLevel.INFO}
    },

    ) {
    suspend fun opprettKrav(body: OpprettInnkrevingsoppdragRequest): HttpResponse = doPost(OPPRETT_KRAV, body)
    suspend fun endreKrav(body: EndringRequest): HttpResponse = doPost(ENDRE_KRAV, body)
    suspend fun stoppKrav(body: AvskrivingRequest):HttpResponse = doPost(STOPP_KRAV, body)

    private suspend inline fun <reified T> doPost(path: String, body: T): HttpResponse {
        val token = tokenProvider.hentAccessToken()
        val response = client.post("$skeEndpoint$path") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("Klientid", KLIENT_ID)
                }
                setBody(body)
            }
            println("resp_body: ${response.bodyAsText()}, \n${response.headers}, \n${response.request.call}")
        return response
    }

    @OptIn(InternalAPI::class)
    private suspend fun doPut(path: String, body: String): HttpResponse {
        val token = tokenProvider.hentAccessToken()
        val response = client.put("$skeEndpoint$path") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Klientid", KLIENT_ID)
            }
            setBody(body)
        }
        println("resp_body: ${response.bodyAsText()}, \n${response.headers}, \n${response.content}, \n${response.request.call}")

        return response
    }

}


