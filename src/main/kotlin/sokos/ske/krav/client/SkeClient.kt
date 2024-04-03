package sokos.ske.krav.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import sokos.ske.krav.domain.ske.requests.*
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.httpClient
import java.util.*

private const val OPPRETT_KRAV = "innkrevingsoppdrag"
private const val ENDRE_RENTER = "innkrevingsoppdrag/%s/renter?kravidentifikatortype=%s"
private const val ENDRE_HOVESTOL = "innkrevingsoppdrag/%s/hovedstol?kravidentifikatortype=%s"
private const val ENDRE_REFERANSENUMMER = "innkrevingsoppdrag/%s/oppdragsgiversreferanse?kravidentifikatortype=%s"
private const val STOPP_KRAV = "innkrevingsoppdrag/avskriving"
private const val MOTTAKSSTATUS = "innkrevingsoppdrag/%s/mottaksstatus?kravidentifikatortype=%s"
private const val VALIDERINGSFEIL = "innkrevingsoppdrag/%s/valideringsfeil?kravidentifikatortype=%s"
private const val HENT_SKE_KRAVIDENT = "innkrevingsoppdrag/%s/avstemming?kravidentifikatortype=OPPDRAGSGIVERS_KRAVIDENTIFIKATOR"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {

    suspend fun endreRenter(
        request: EndreRenteBeloepRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String
    ) = doPut(String.format(ENDRE_RENTER, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun endreHovedstol(
        request: NyHovedStolRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String
    ) = doPut(String.format(ENDRE_HOVESTOL, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun endreOppdragsGiversReferanse(
        request: NyOppdragsgiversReferanseRequest,
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        corrID: String
    ) = doPut(String.format(ENDRE_REFERANSENUMMER, kravidentifikator, kravidentifikatorType.value), request, kravidentifikator, corrID)

    suspend fun opprettKrav(request: OpprettInnkrevingsoppdragRequest, corrID: String) =
        doPost(OPPRETT_KRAV, request, corrID)

    suspend fun stoppKrav(request: AvskrivingRequest, corrID: String) = doPost(STOPP_KRAV, request, corrID)

    suspend fun getMottaksStatus(kravid: String, kravidentifikatorType: KravidentifikatorType) =
        doGet(String.format(MOTTAKSSTATUS, kravid, kravidentifikatorType.value), UUID.randomUUID().toString())

    suspend fun getValideringsfeil(kravid: String, kravidentifikatorType: KravidentifikatorType) =
        doGet(String.format(VALIDERINGSFEIL, kravid, kravidentifikatorType.value), UUID.randomUUID().toString())

    suspend fun getSkeKravidentifikator(referanse: String) =
        doGet(String.format(HENT_SKE_KRAVIDENT, referanse), UUID.randomUUID().toString())

    private suspend inline fun <reified T> doPost(path: String, request: T, corrID: String) = client.post(
        buildHttpRequest(path, corrID).apply {
            contentType(ContentType.Application.Json)
            setBody(request)
        },
    )

    private suspend inline fun <reified T> doPut(path: String, request: T, kravidentifikator: String, corrID: String) =
        client.put(
            buildHttpRequest(path, corrID).apply {
                contentType(ContentType.Application.Json)
                headers.append("kravidentifikator", kravidentifikator)
                setBody(request)
            },
        )

    private suspend fun doGet(path: String, corrID: String) = client.get(buildHttpRequest(path, corrID))

    private suspend fun buildHttpRequest(path: String, corrID: String): HttpRequestBuilder {
        val token = tokenProvider.hentAccessToken()
        return HttpRequestBuilder().apply {
            url("$skeEndpoint$path")
            accept(ContentType.Application.Json)
            headers {
                append("Klientid", KLIENT_ID)
                append("Korrelasjonsid", corrID)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
