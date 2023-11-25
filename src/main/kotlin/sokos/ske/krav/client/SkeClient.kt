package sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.EndreHovedStolRequest
import sokos.ske.krav.domain.ske.requests.EndreRenterRequest
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.httpClient
import java.util.UUID

private const val OPPRETT_KRAV = "innkrevingsoppdrag"
private const val ENDRE_RENTER = "innkrevingsoppdrag/%s/renter?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val ENDRE_HOVESTOL = "innkrevingsoppdrag/%s/hovedstol?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val STOPP_KRAV = "innkrevingsoppdrag/avskriving"
private const val MOTTAKSSTATUS =
    "innkrevingsoppdrag/%s/mottaksstatus?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val VALIDERINGSFEIL =
    "innkrevingsoppdrag/%s/valideringsfeil?kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {

    suspend fun endreRenter(request: EndreRenterRequest, kravid: String): HttpResponse = doPut(String.format(ENDRE_RENTER, kravid), request, kravid)
    suspend fun endreHovedstol(request: EndreHovedStolRequest, kravid: String): HttpResponse = doPut(String.format(ENDRE_HOVESTOL, kravid), request, kravid)
    suspend fun opprettKrav(request: OpprettInnkrevingsoppdragRequest): HttpResponse = doPost(OPPRETT_KRAV, request) //  suspend fun stoppKrav(body: String): HttpResponse = doPost(STOPP_KRAV, body)
    suspend fun stoppKrav(request: AvskrivingRequest): HttpResponse = doPost(STOPP_KRAV, request)
    suspend fun hentMottaksStatus(kravid: String) = doGet(String.format(MOTTAKSSTATUS, kravid))
    suspend fun hentValideringsfeil(kravid: String) = doGet(String.format(VALIDERINGSFEIL, kravid))

    private suspend inline fun <reified T> doPost(path: String, request: T): HttpResponse = client.post(
        buildRequest(path).apply {
            setBody(request)
        },
    )

    private suspend inline fun<reified T> doPut(path: String, request: T, kravidentifikator: String): HttpResponse = client.put(
        buildRequest(path).apply {
            headers.append("kravidentifikator", kravidentifikator)
            setBody(request)
        },
    )

    private suspend fun doGet(path: String): HttpResponse = client.get(buildRequest(path))

    private suspend fun buildRequest(path: String): HttpRequestBuilder {
        val token = tokenProvider.hentAccessToken()
        return HttpRequestBuilder().apply {
            url("$skeEndpoint$path")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            headers {
                append("Klientid", KLIENT_ID)
                append("Korrelasjonsid", UUID.randomUUID().toString())
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
