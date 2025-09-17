package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotliquery.Session
import mu.KotlinLogging

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.AvstemmingResponse
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.metrics.Metrics.incrementMetrics
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.util.createEndreHovedstolRequest
import no.nav.sokos.ske.krav.util.createEndreRenteRequest
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.createStoppKravRequest
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString
import no.nav.sokos.ske.krav.util.parseTo

private val logger = KotlinLogging.logger {}

const val IKKE_SENT_KRAV = "IKKE_SENT_KRAV"
const val KRAV_FOR_STATUS_CHECK = "KRAV_FOR_STATUS_CHECK"

class KravService(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
    private val kravRepository: KravRepository = KravRepository(dataSource),
    private val feilmeldingRepository: FeilmeldingRepository = FeilmeldingRepository(dataSource),
    private val skeClient: SkeClient = SkeClient(),
    private val statusService: StatusService = StatusService(dataSource, skeClient, kravRepository),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun sendKrav(kravListe: List<Krav>): List<RequestResult> {
        if (kravListe.isNotEmpty()) logger.info("Sender ${kravListe.size}")

        return dataSource.transaction { session ->
            val responseList =
                sendAllOpprettKrav(kravListe.filter { it.kravtype == NYTT_KRAV }, session) +
                    sendAllEndreKrav(kravListe.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }, session) +
                    sendAllStoppKrav(kravListe.filter { it.kravtype == STOPP_KRAV }, session)

            handleErrors(responseList, session)
            responseList
        }
    }

    suspend fun resendKrav() {
        statusService.getMottaksStatus()
        kravRepository
            .getAllKravForResending()
            .takeIf { kravListe -> kravListe.isNotEmpty() }
            ?.let { kravListe ->
                logger.info("Resender ${kravListe.size} krav")
                val requestResultList = sendKrav(kravListe)
                Metrics.numberOfKravResent.increment(requestResultList.size.toDouble())
            }
    }

    fun getKravListe(kravType: String): List<Krav> =
        when (kravType) {
            IKKE_SENT_KRAV -> kravRepository.getAllUnsentKrav()
            KRAV_FOR_STATUS_CHECK -> kravRepository.getAllKravForStatusCheck()
            else -> emptyList()
        }

    suspend fun opprettKravFraFilOgOppdatereStatus(
        kravlinjeListe: List<KravLinje>,
        fileName: String,
    ) {
        dataSource.transaction { session ->
            kravRepository.insertAllNewKrav(kravlinjeListe, fileName, session)
        }

        kravlinjeListe.forEach { krav ->
            val skeKravidentifikator = getSkeKravidentifikator(krav.referansenummerGammelSak)
            var skeKravidentifikatorSomSkalLagres = skeKravidentifikator

            if (skeKravidentifikatorSomSkalLagres.isBlank()) {
                val httpResponse = skeClient.getSkeKravidentifikator(krav.referansenummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    skeKravidentifikatorSomSkalLagres = httpResponse.parseTo<AvstemmingResponse>()?.kravidentifikator ?: ""
                }
            }

            if (skeKravidentifikatorSomSkalLagres.isNotBlank()) {
                dataSource.transaction { session ->
                    kravRepository.updateEndringWithSkeKravIdentifikator(krav.saksnummerNav, skeKravidentifikatorSomSkalLagres, session)
                }
            } else {
                slackService.addError(
                    fileName,
                    "Fant ikke gyldig kravidentifikator for migrert krav",
                    Pair(
                        "Fant ikke gyldig kravidentifikator for migrert krav",
                        "Saksnummer: ${krav.saksnummerNav} \n ReferansenummerGammelSak: ${krav.referansenummerGammelSak} \n Dette må følges opp manuelt",
                    ),
                )
                logger.error { "Fant ikke gyldig kravidentifikator for migrert krav:  ${krav.referansenummerGammelSak} " }
            }
        }
    }

    private fun getSkeKravidentifikator(navref: String): String =
        kravRepository.getSkeKravidentifikator(navref).ifBlank {
            kravRepository
                .getPreviousReferansenummer(navref)
                .takeIf { it.isNotBlank() }
                ?.let { kravRepository.getSkeKravidentifikator(it) }
                ?: ""
        }

    private suspend fun sendAllOpprettKrav(
        kravList: List<Krav>,
        session: Session,
    ): List<RequestResult> =
        kravList
            .map { krav -> sendOpprettKrav(krav) }
            .also { results ->
                incrementMetrics(results)
                results.forEach { result ->
                    kravRepository.updateSentKravStatusMedKravIdentifikator(
                        corrId = result.krav.corrId,
                        skeKravidentifikator = result.kravidentifikator,
                        responseStatus = result.status.value,
                        session = session,
                    )
                    Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)
                }
            }

    private suspend fun sendAllEndreKrav(
        kravList: List<Krav>,
        session: Session,
    ): List<RequestResult> =
        kravList
            .groupBy { it.kravidentifikatorSKE + it.saksnummerNAV }
            .flatMap { (_, groupedKrav) ->
                val kravidentifikatorPair = createKravidentifikatorPair(groupedKrav.first())
                val response =
                    groupedKrav.map {
                        sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it)
                    }
                getConformedResponses(response)
            }.also { results ->
                incrementMetrics(results)
                results.forEach { result ->
                    kravRepository.updateSentKravStatus(result.krav.corrId, result.status.value, session)
                    Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)
                }
            }

    private suspend fun sendAllStoppKrav(
        kravList: List<Krav>,
        session: Session,
    ): List<RequestResult> =
        kravList
            .map { krav -> sendStoppKrav(krav) }
            .also { results ->
                incrementMetrics(results)
                results.forEach { result ->
                    kravRepository.updateSentKravStatus(result.krav.corrId, result.status.value, session)
                    Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)
                }
            }

    private suspend fun handleErrors(
        responses: List<RequestResult>,
        session: Session,
    ) {
        responses
            .filterNot { it.response.status.isSuccess() }
            .map { result ->
                val skeKravidentifikator =
                    if (result.kravidentifikator == result.krav.saksnummerNAV || result.kravidentifikator == result.krav.referansenummerGammelSak) {
                        ""
                    } else {
                        result.kravidentifikator
                    }

                val feilResponse = result.response.parseTo<FeilResponse>() ?: return
                Feilmelding(
                    0L,
                    kravRepository.getKravTableIdFromCorrelationId(result.krav.corrId),
                    result.krav.corrId,
                    result.krav.saksnummerNAV,
                    skeKravidentifikator,
                    feilResponse.status.toString(),
                    feilResponse.detail,
                    result.request,
                    result.response.bodyAsText(),
                    LocalDateTime.now(),
                )
            }.takeIf { it.isNotEmpty() }
            ?.run {
                feilmeldingRepository.insertFeilmeldinger(this, session)

                responses.forEach { result ->
                    result.response.parseTo<FeilResponse>()?.let { feilResponse ->
                        val errorPair = Pair(feilResponse.title, feilResponse.detail)
                        slackService.addError(result.krav.filnavn, "Feil fra SKE", errorPair)
                    }
                }
            }
    }

    private fun getConformedResponses(requestResults: List<RequestResult>): List<RequestResult> {
        val endring1 = requestResults.first()
        val endring2 = requestResults.last()

        if (endring1.status == endring2.status) return requestResults

        val newStatus =
            determineNewStatus(
                Pair(endring1.response.status.value, endring1.status),
                Pair(endring2.response.status.value, endring2.status),
            )

        return listOf(endring1, endring2).map { it.copy(status = newStatus) }
    }

    private fun determineNewStatus(
        endring1: Pair<Int, Status>,
        endring2: Pair<Int, Status>,
    ): Status =
        when {
            endring1.first == HttpStatusCode.NotFound.value -> endring1.second
            endring2.first == HttpStatusCode.NotFound.value -> endring2.second
            endring1.first == HttpStatusCode.UnprocessableEntity.value -> endring1.second
            endring2.first == HttpStatusCode.UnprocessableEntity.value -> endring2.second
            endring1.first == HttpStatusCode.Conflict.value -> endring1.second
            endring2.first == HttpStatusCode.Conflict.value -> endring2.second
            else -> Status.UKJENT_STATUS
        }

    private suspend fun sendEndreKrav(
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        krav: Krav,
    ): RequestResult {
        val (response, request) =
            if (krav.kravtype == ENDRING_RENTE) {
                val request = createEndreRenteRequest(krav)
                val response = skeClient.endreRenter(request, kravidentifikator, kravidentifikatorType, krav.corrId)
                Pair(response, request.encodeToString())
            } else {
                val request = createEndreHovedstolRequest(krav)
                val response = skeClient.endreHovedstol(request, kravidentifikator, kravidentifikatorType, krav.corrId)
                Pair(response, request.encodeToString())
            }

        return RequestResult(
            response = response,
            request = request,
            krav = krav,
            kravidentifikator = "",
            status = defineStatus(response),
        )
    }

    private suspend fun sendOpprettKrav(krav: Krav): RequestResult {
        val opprettKravRequest = createOpprettKravRequest(krav)
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)

        return RequestResult(
            response = response,
            request = opprettKravRequest.encodeToString(),
            krav = krav,
            kravidentifikator = response.parseTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: "",
            status = defineStatus(response),
        )
    }

    private suspend fun sendStoppKrav(krav: Krav): RequestResult {
        val kravidentifikatorPair = createKravidentifikatorPair(krav)
        val request = createStoppKravRequest(kravidentifikatorPair.first, kravidentifikatorPair.second)
        val response = skeClient.stoppKrav(request, krav.corrId)

        return RequestResult(
            response = response,
            request = request.encodeToString(),
            krav = krav,
            kravidentifikator = kravidentifikatorPair.first,
            status = defineStatus(response),
        )
    }
}
