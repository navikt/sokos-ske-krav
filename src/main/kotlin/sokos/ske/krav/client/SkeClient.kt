package sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.httpClient
import java.util.UUID

private const val BASE_URL = "innkrevingsoppdrag"
private const val OPPRETT_KRAV = BASE_URL
private const val ENDRE_RENTER = "$BASE_URL/%s/renter?kravidentifikatortype=%s"
private const val ENDRE_HOVESTOL = "$BASE_URL/%s/hovedstol?kravidentifikatortype=%s"
private const val STOPP_KRAV = "$BASE_URL/avskriving"
private const val MOTTAKSSTATUS = "$BASE_URL/%s/mottaksstatus?kravidentifikatortype=%s"
private const val VALIDERINGSFEIL = "$BASE_URL/%s/valideringsfeil?kravidentifikatortype=%s"
private const val HENT_SKE_KRAVIDENT = "$BASE_URL/%s/avstemming?kravidentifikatortype=OPPDRAGSGIVERS_KRAVIDENTIFIKATOR"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient),
    private val skeEndpoint: String = PropertiesConfig.SKEConfig.skeRestUrl,
    private val client: HttpClient = httpClient,
) {
    suspend fun endreRenter(
        request: EndreRenteBeloepRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String,
    ) = doPut(createUrl(ENDRE_RENTER, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun endreHovedstol(
        request: NyHovedStolRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String,
    ) = doPut(createUrl(ENDRE_HOVESTOL, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun opprettKrav(
        request: OpprettInnkrevingsoppdragRequest,
        corrID: String,
    ) = doPost(OPPRETT_KRAV, request, corrID)

    suspend fun stoppKrav(
        request: AvskrivingRequest,
        corrID: String,
    ) = doPost(STOPP_KRAV, request, corrID)

    suspend fun getMottaksStatus(
        kravid: String,
        kravidentifikatorType: KravidentifikatorType,
    ) = doGet(path = createUrl(MOTTAKSSTATUS, kravid, kravidentifikatorType.value))

    suspend fun getValideringsfeil(
        kravid: String,
        kravidentifikatorType: KravidentifikatorType,
    ) = doGet(path = createUrl(VALIDERINGSFEIL, kravid, kravidentifikatorType.value))

    suspend fun getSkeKravidentifikator(referanse: String) = doGet(path = createUrl(HENT_SKE_KRAVIDENT, referanse))

    private suspend inline fun <reified T> doPost(
        path: String,
        request: T,
        corrID: String,
    ) = client.post(path) {
        addDefaultHeaders(corrID)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    private suspend inline fun <reified T> doPut(
        path: String,
        request: T,
        kravidentifikator: String,
        corrID: String,
    ) = client.put(path) {
        addDefaultHeaders(corrID)
        contentType(ContentType.Application.Json)
        headers.append("kravidentifikator", kravidentifikator)
        setBody(request)
    }

    private suspend fun doGet(
        path: String,
        corrID: String = UUID.randomUUID().toString(),
    ) = client.get(path) {
        addDefaultHeaders(corrID)
    }

    private suspend fun HttpRequestBuilder.addDefaultHeaders(correlationId: String) {
        val token = tokenProvider.hentAccessToken()
        headers.append("Klientid", "NAV/0.1")
        headers.append("Korrelasjonsid", correlationId)
        headers.append(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun createUrl(
        template: String,
        vararg args: Any,
    ): String = "$skeEndpoint${String.format(template, *args)}"
}
