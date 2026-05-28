package no.nav.sokos.ske.krav.service

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.responses.AvstemmingResponse
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.KRAV_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.decodeTo
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.LineValidator

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

private val logger = mu.KotlinLogging.logger {}

class SkeService(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val statusService: StatusService = StatusService(skeClient = skeClient),
    private val stoppKravService: StoppKravService = StoppKravService(skeClient),
    private val endreKravService: EndreKravService = EndreKravService(skeClient),
    private val opprettKravService: OpprettKravService = OpprettKravService(skeClient),
    private val slackService: SlackService = SlackService(),
    private val ftpService: FtpService = FtpService(),
    private val filValideringsfeilRepository: FilValideringsfeilRepository = FilValideringsfeilRepository.instance,
    private val feilmeldingRepository: FeilmeldingRepository = FeilmeldingRepository.instance,
    private val kravRepository: KravRepository = KravRepository.instance,
) {
    private var haltRun = false

    suspend fun handleNewKrav() {
        if (haltRun) {
            logger.info("*** Kjøring er blokkert ***")
            return
        }

        resendKrav()
        sendNewFilesToSKE()
        delay(5000.milliseconds)
        resendKrav()

        slackService.sendErrors()

        if (haltRun) {
            haltRun = false
            logger.info("*** Kjøring er ublokkert ***")
        }
    }

    private suspend fun resendKrav() {
        statusService.getMottaksStatus()
        val allKravForResending = kravRepository.getAllKravForResending()
        if (allKravForResending.isEmpty()) return

        logger.info("Resender ${allKravForResending.size} krav")
        sendKrav(allKravForResending).also {
            Metrics.numberOfKravResent.increment(it.size.toDouble())
        }
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        val filtekst = if (files.size == 1) "fil" else "filer"
        if (files.isNotEmpty()) {
            val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
            logger.info("*** Starter sending av ${files.size} $filtekst $datetime***")
        } else {
            logger.info("*** Ingen nye filer ***")
        }

        files.forEach { file ->
            processFile(file)
        }

        if (files.isNotEmpty()) {
            updateSkeKravidentifikatorForEndringerAndStopp()
            sendKrav(kravRepository.getAllUnsentKrav()).also(::logResult)
            logger.info { "*** Ferdig med sending av ${files.size} $filtekst ***" }
        }
    }

    private suspend fun processFile(file: FtpFil) {
        logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
        val validatedLines = LineValidator(dataSource, filValideringsfeilRepository).validateNewLines(file)

        handleValidationResults(file, validatedLines)

        dataSource.transaction { session ->
            kravRepository.insertAllNewKrav(session, validatedLines, file.name)
        }
        ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)
    }

    private suspend fun sendKrav(kravList: List<Krav>): List<RequestResult> {
        if (kravList.isNotEmpty()) logger.info("Sender ${kravList.size}")

        val allResponses =
            opprettKravService.sendAllOpprettKrav(kravList.filter { it.kravtype == NYTT_KRAV }) +
                endreKravService.sendAllEndreKrav(kravList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }) +
                stoppKravService.sendAllStoppKrav(kravList.filter { it.kravtype == STOPP_KRAV })

        updateSentKrav(allResponses)
        handleErrors(allResponses)
        return allResponses
    }

    private fun updateSentKrav(requestResults: List<RequestResult>) {
        incrementMetrics(requestResults)
        requestResults.forEach { result ->
            Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)

            val skeKravidentifikator = if (result.krav.kravtype == NYTT_KRAV) result.kravidentifikator.takeIf { it.isNotBlank() } else null
            dataSource.transaction { session ->
                kravRepository.updateSentKrav(session, result.krav.corrId, result.status, skeKravidentifikator)
            }
        }
    }

    private suspend fun updateSkeKravidentifikatorForEndringerAndStopp() {
        val unsentEndringerAndStopp = kravRepository.getAllUnsentEndringerAndStopp()
        val requestResults = mutableListOf<RequestResult>()
        val slackErrorsHandled = mutableSetOf<String>()

        unsentEndringerAndStopp.forEach { krav ->
            // Sjekke om vi har det opprinnelige kravet i vår database
            val skeKravidentifikator = kravRepository.getSkeKravidentifikator(krav.referansenummerGammelSak)
            if (skeKravidentifikator.isNotBlank()) {
                dataSource.transaction { session ->
                    kravRepository.updateEndringWithSkeKravIdentifikator(session, krav.saksnummerNAV, skeKravidentifikator)
                }
                return@forEach
            }

            val requestResult = getKravidentifikatorFromSkatt(krav)

            // Feil som 403, 500 osv skal håndteres som normalt
            if (requestResult.status != Status.HTTP404_FANT_IKKE_SAKSREF) requestResults.add(requestResult)

            // Vi fant kravidentifikator fra SKE
            if (requestResult.kravidentifikator.isNotBlank()) {
                dataSource.transaction { session ->
                    kravRepository.updateEndringWithSkeKravIdentifikator(session, krav.saksnummerNAV, requestResult.kravidentifikator)
                }
            } else {
                dataSource.transaction { session ->
                    kravRepository.updateStatus(session, requestResult.status, krav.corrId)
                }

                // Fra SKE vil vi få feilmeldingen "innkrevingsoppdrag eksisterer ikke" men vi ønsker mer tydelig informasjon samt informasjonen vi trenger for å kunne følge det opp manuelt.
                if (requestResult.status == Status.HTTP404_FANT_IKKE_SAKSREF) {
                    handle404FromAvstemming(requestResult, krav, slackErrorsHandled)
                }
            }
        }
        handleErrors(requestResults)
    }

    private fun handle404FromAvstemming(
        requestResult: RequestResult,
        krav: Krav,
        slackErrorsHandled: MutableSet<String>,
    ) {
        val shouldAlert = slackErrorsHandled.add(krav.saksnummerNAV)

        handleError(
            requestResult,
            FeilResponse(
                type = requestResult.feilResponse?.type ?: KRAV_EKSISTERER_IKKE,
                title = FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR,
                status = requestResult.httpStatusCode.value,
                detail = "Innkrevingsoppdrag med referansenummerGammelSak ${krav.referansenummerGammelSak} eksisterer ikke. \n Nav-Saksnummer: ${krav.saksnummerNAV} \n  Dette må følges opp manuelt",
                instance = requestResult.feilResponse?.instance ?: "custom",
            ),
            shouldAlert,
        )

        if (shouldAlert) {
            logger.warn { "Fant ikke gyldig kravidentifikator for krav med referansenummerGammelSak: ${requestResult.krav.referansenummerGammelSak} " }
        }
    }

    private suspend fun getKravidentifikatorFromSkatt(krav: Krav): RequestResult {
        val responseAvstemmingSkatt = skeClient.getSkeKravidentifikator(krav.referansenummerGammelSak)
        val responseBody = responseAvstemmingSkatt.bodyAsText()
        val responseStatus = responseAvstemmingSkatt.status
        val definertStatus = defineStatus(responseBody, responseStatus)

        val kravidentifikator = if (responseStatus.isSuccess()) responseBody.decodeTo<AvstemmingResponse>()?.kravidentifikator ?: "" else ""

        // Ikke forsøk å sende kravet hvis kravidentifikator mangler eller respons ikke kan parses
        val statusToSet =
            when {
                responseStatus != HttpStatusCode.OK -> definertStatus.first
                kravidentifikator.isEmpty() -> Status.UKJENT_FEIL
                else -> krav.status
            }

        return RequestResult(
            responseBody = responseBody,
            httpStatusCode = responseStatus,
            request = krav.referansenummerGammelSak,
            krav = krav,
            kravidentifikator = kravidentifikator,
            status = statusToSet,
            feilResponse = definertStatus.second,
        )
    }

    private fun handleValidationResults(
        file: FtpFil,
        validatedLines: List<KravLinje>,
    ) {
        if (file.kravLinjer.size > validatedLines.size) {
            logger.warn("Ved validering av linjer i fil ${file.name} har ${file.kravLinjer.size - validatedLines.size} linjer velideringsfeil ")
        }
        if (validatedLines.size >= 1000) {
            logger.info("***Stor fil. Blokkerer kjøring***")
            haltRun = true
        }
    }

    private fun handleError(
        requestResult: RequestResult,
        feilResponse: FeilResponse?,
        shouldAlert: Boolean = true,
    ) {
        if (shouldAlert) {
            val errorPair = feilResponse?.let { Pair(feilResponse.title, feilResponse.detail) } ?: Pair("Ukjent feil", "Kunne ikke parse feilresponse")
            slackService.addError(requestResult.krav.filnavn, "Feil fra SKE", errorPair)
        }

        saveErrorMessage(
            requestResult.request,
            requestResult.responseBody,
            requestResult.httpStatusCode,
            requestResult.krav,
            requestResult.kravidentifikator,
            feilResponse,
        )
    }

    private fun handleErrors(responses: List<RequestResult>) {
        responses
            .filterNot { it.httpStatusCode.isSuccess() }
            .forEach { result ->
                handleError(result, result.feilResponse)
            }
    }

    private fun saveErrorMessage(
        request: String,
        response: String,
        status: HttpStatusCode,
        krav: Krav,
        kravidentifikator: String,
        feilResponse: FeilResponse? = null,
    ) {
        val skeKravidentifikator =
            if (kravidentifikator == krav.saksnummerNAV || kravidentifikator == krav.referansenummerGammelSak) "" else kravidentifikator

        val resolvedFeilResponse = feilResponse ?: response.decodeTo<FeilResponse>() ?: FeilResponse("egendefinert", "Feil i parsing av http respons", status.value, response, "")

        dataSource.transaction { session ->
            val kravId = kravRepository.getKravTableIdFromCorrelationId(session, krav.corrId)
            val feilmelding =
                Feilmelding(
                    0L,
                    kravId,
                    krav.corrId,
                    krav.saksnummerNAV,
                    skeKravidentifikator,
                    resolvedFeilResponse.status.toString(),
                    resolvedFeilResponse.detail,
                    request,
                    response,
                    LocalDateTime.now(),
                )
            feilmeldingRepository.insertFeilmelding(session, feilmelding)
        }
    }

    fun logTooLongWithoutReskontrofort() {
        val now = LocalDateTime.now()
        val kravResentForOverOneDay =
            kravRepository
                .getAllKravForStatusCheck()
                .filter { it.tidspunktSendt?.isBefore((now.minusHours(24))) == true }

        if (kravResentForOverOneDay.isEmpty()) return
        val kravGroupedPerDay = kravResentForOverOneDay.groupBy { Duration.between(it.tidspunktSendt, now).toDays() }

        val logMessage =
            buildString {
                append("${kravResentForOverOneDay.size} krav er blitt forsøkt resendt i over 24 timer: \n")
                kravGroupedPerDay.toSortedMap().forEach { (day, kravPerDay) ->
                    kravPerDay.groupBy { it.avsender }.toSortedMap().forEach { (avsender, krav) ->
                        append("${krav.size} krav fra $avsender har blitt forsøkt resendt i $day dag(er)\n")
                    }
                }
            }

        logger.error { logMessage }
    }

    private fun logResult(result: List<RequestResult>) {
        result.partition { it.httpStatusCode.isSuccess() }.also { (successful, unsuccessful) ->
            successful.aggregertPerFil().forEach { (filnavn, count) ->
                logger.info("Fil: $filnavn - Nye: ${count.new}, Endringer: ${count.changes}, Stopp: ${count.stops}")
            }
            unsuccessful.aggregertPerFil().forEach { (filnavn, count) ->
                logger.info("Ikke vellykkede - Fil: $filnavn - Nye: ${count.new}, Endringer: ${count.changes}, Stopp: ${count.stops}")
            }
            logger.info("Sendte ${result.size} krav${if (unsuccessful.isNotEmpty()) ". ${unsuccessful.size} feilet" else ""}")
        }
    }

    internal companion object {
        internal data class Counts(
            val new: Int,
            val changes: Int,
            val stops: Int,
        )

        internal fun List<RequestResult>.aggregertPerFil(): Map<String, Counts> =
            groupBy { it.krav.filnavn }
                .mapValues { (_, resultaterPerFil) ->
                    val antallNye = resultaterPerFil.count { it.krav.kravtype == NYTT_KRAV }
                    val antallEndringer = resultaterPerFil.count { it.krav.kravtype == ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }
                    val antallStopp = resultaterPerFil.count { it.krav.kravtype == STOPP_KRAV }

                    Counts(antallNye, antallEndringer, antallStopp)
                }
    }

    private fun incrementMetrics(results: List<RequestResult>) {
        Metrics.numberOfKravSent.increment(results.size.toDouble())
        Metrics.numberOfKravFeilet.increment(results.filter { !it.httpStatusCode.isSuccess() }.size.toDouble())
        Metrics.numberOfNyeKrav.increment(results.filter { it.krav.kravtype == NYTT_KRAV }.size.toDouble())
        Metrics.numberOfEndringerAvKrav.increment(results.filter { it.krav.kravtype == ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }.size.toDouble())
        Metrics.numberOfStoppAvKrav.increment(results.filter { it.krav.kravtype == STOPP_KRAV }.size.toDouble())
    }
}
