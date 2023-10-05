package sokos.ske.krav.client

import io.ktor.client.*

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.skemodels.requests.AvskrivingRequest
import sokos.ske.krav.skemodels.requests.EndringRequest
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest

private const val OPPRETT_KRAV = "innkrevingsoppdrag"
private const val ENDRE_KRAV = "innkrevingsoppdrag/endring"
private const val STOPP_KRAV = "innkrevingsoppdrag/avskriving"
private const val MOTTAKSSTATUS = "innkrevingsoppdrag/%s/mottaksstatus?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val VALIDERINGSFEIL = "innkrevingsoppdrag/%s/valideringsfeil?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val KLIENT_ID = "NAV/0.1"

private val logger = KotlinLogging.logger {}

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = defaultHttpClient
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val builder = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private inline fun <reified T> toJson(serializer: SerializationStrategy<T>, body: T) =
        builder.encodeToJsonElement(serializer, body).toString()

    suspend fun opprettKrav(body: OpprettInnkrevingsoppdragRequest): HttpResponse =
        doPost(OPPRETT_KRAV, toJson(OpprettInnkrevingsoppdragRequest.serializer(), body))
    suspend fun stoppKrav(body: AvskrivingRequest): HttpResponse =
        doPost(STOPP_KRAV, toJson(AvskrivingRequest.serializer(), body))

    suspend fun endreKrav(body: EndringRequest): HttpResponse =
        doPut(ENDRE_KRAV, toJson(EndringRequest.serializer(), body))


    suspend fun hentMottaksStatus(kravid: String) = doGet(String.format(MOTTAKSSTATUS, kravid))
    suspend fun hentValideringsfeil(kravid: String) = doGet(String.format(VALIDERINGSFEIL, kravid))


    private suspend inline fun doPost(path: String, body: String): HttpResponse {
        val token = tokenProvider.hentAccessToken()

        println("\n\nToken: -" + token + "-\n\n")
        logger.info { "logger doPost body: $body" }
        val response = client.post("$skeEndpoint$path") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Klientid", KLIENT_ID)
            }
            setBody(body)
        }
        println("resp_body: ${response.bodyAsText()}, \n${response.headers}, \n${response.request.call}")
        return response
    }

    private suspend fun doPut(path: String, body: String): HttpResponse {
        val token = tokenProvider.hentAccessToken()
        logger.info { "doPut: $body"}
        val response = client.put("$skeEndpoint$path") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Klientid", KLIENT_ID)
            }
            setBody(body)
        }

        println("resp_body: ${response.bodyAsText()}, \n${response.request.call}")
        return response
    }

    private suspend fun doGet(path: String): HttpResponse {
        val token = tokenProvider.hentAccessToken()
        logger.info {"Logger doGet: Path: $skeEndpoint$path"}
        println("\n\nToken: -" + token + "-\n\n")
        val response = client.get("$skeEndpoint$path") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Klientid", KLIENT_ID)
            }
        }
        val bd= response.bodyAsText()

        logger.info { "Logger doGet: resp_body: ${bd}, \n${response.request.call}"}
        return response
    }
}


