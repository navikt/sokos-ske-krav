package no.nav.sokos.ske.krav.client

import java.util.UUID

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.httpClient
import no.nav.sokos.ske.krav.dto.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.dto.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.requests.NyHovedStolRequest
import no.nav.sokos.ske.krav.dto.ske.requests.OpprettInnkrevingsoppdragRequest
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider

const val OPPRETT_KRAV_URL = "innkrevingsoppdrag"
const val ENDRE_RENTER_URL = "innkrevingsoppdrag/%s/renter?kravidentifikatortype=%s"
const val ENDRE_HOVESTOL_URL = "innkrevingsoppdrag/%s/hovedstol?kravidentifikatortype=%s"
const val STOPP_KRAV_URL = "innkrevingsoppdrag/avskriving"
const val MOTTAKSSTATUS_URL = "innkrevingsoppdrag/%s/mottaksstatus?kravidentifikatortype=%s"
const val VALIDERINGSFEIL_URL = "innkrevingsoppdrag/%s/valideringsfeil?kravidentifikatortype=%s"
const val HENT_SKE_KRAVIDENT_URL = "innkrevingsoppdrag/%s/avstemming?kravidentifikatortype=OPPDRAGSGIVERS_KRAVIDENTIFIKATOR"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenProvider = MaskinportenAccessTokenProvider(PropertiesConfig.MaskinportenClientConfig(), httpClient),
    private val skeEndpoint: String = PropertiesConfig.SKEConfig.skeRestUrl,
    private val client: HttpClient = httpClient,
) {
    suspend fun endreRenter(
        request: EndreRenteBeloepRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String,
    ) = doPut(String.format(ENDRE_RENTER_URL, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun endreHovedstol(
        request: NyHovedStolRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String,
    ) = doPut(String.format(ENDRE_HOVESTOL_URL, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun opprettKrav(
        request: OpprettInnkrevingsoppdragRequest,
        corrID: String,
    ) = doPost(OPPRETT_KRAV_URL, request, corrID)

    suspend fun stoppKrav(
        request: AvskrivingRequest,
        corrID: String,
    ) = doPost(STOPP_KRAV_URL, request, corrID)

    suspend fun getMottaksStatus(
        kravid: String,
        kravidentifikatorType: KravidentifikatorType,
    ) = doGet(String.format(MOTTAKSSTATUS_URL, kravid, kravidentifikatorType.value), UUID.randomUUID().toString())

    suspend fun getValideringsfeil(
        kravid: String,
        kravidentifikatorType: KravidentifikatorType,
    ) = doGet(String.format(VALIDERINGSFEIL_URL, kravid, kravidentifikatorType.value), UUID.randomUUID().toString())

    suspend fun getSkeKravidentifikator(referanse: String) = doGet(String.format(HENT_SKE_KRAVIDENT_URL, referanse), UUID.randomUUID().toString())

    private suspend inline fun <reified T> doPost(
        path: String,
        request: T,
        corrID: String,
    ) = client.post(
        buildHttpRequest(path, corrID).apply {
            contentType(ContentType.Application.Json)
            setBody(request)
        },
    )

    private suspend inline fun <reified T> doPut(
        path: String,
        request: T,
        kravidentifikator: String,
        corrID: String,
    ) = client.put(
        buildHttpRequest(path, corrID).apply {
            contentType(ContentType.Application.Json)
            headers.append("kravidentifikator", kravidentifikator)
            setBody(request)
        },
    )

    private suspend fun doGet(
        path: String,
        corrID: String,
    ) = client.get(buildHttpRequest(path, corrID))

    private suspend fun buildHttpRequest(
        path: String,
        corrID: String,
    ): HttpRequestBuilder {
        val token = tokenProvider.getAccessToken()
        return HttpRequestBuilder().apply {
            url("$skeEndpoint$path")
            headers {
                append("Klientid", KLIENT_ID)
                append("Korrelasjonsid", corrID)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
