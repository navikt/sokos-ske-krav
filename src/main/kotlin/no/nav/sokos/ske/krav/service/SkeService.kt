package no.nav.sokos.ske.krav.service

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

import com.zaxxer.hikari.HikariDataSource
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
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.DBUtils.asyncTransaction
import no.nav.sokos.ske.krav.util.KRAV_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.decodeTo
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.validation.LineValidator

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

private val logger = mu.KotlinLogging.logger {}

class SkeService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val statusService: StatusService = StatusService(skeClient = skeClient, databaseService = databaseService),
    private val stoppKravService: StoppKravService = StoppKravService(skeClient, databaseService),
    private val endreKravService: EndreKravService = EndreKravService(skeClient, databaseService),
    private val opprettKravService: OpprettKravService = OpprettKravService(skeClient, databaseService),
    private val slackService: SlackService = SlackService(),
    private val ftpService: FtpService = FtpService(),
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
        databaseService.getAllKravForResending().takeIf { it.isNotEmpty() }?.let {
            logger.info("Resender ${it.size} krav")
            Metrics.numberOfKravResent.increment(sendKrav(it).size.toDouble())
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
            sendKrav(databaseService.getAllUnsentKrav()).also { logResult(it) }
            logger.info { "*** Ferdig med sending av ${files.size} $filtekst ***" }
        }
    }

    private suspend fun processFile(file: FtpFil) {
        logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
        val validatedLines = LineValidator().validateNewLines(file, databaseService)

        handleValidationResults(file, validatedLines)

        databaseService.saveAllNewKrav(validatedLines, file.name)
        ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)
    }

    private suspend fun sendKrav(kravList: List<Krav>): List<RequestResult> {
        if (kravList.isNotEmpty()) logger.info("Sender ${kravList.size}")

        val allResponses =
            opprettKravService.sendAllOpprettKrav(kravList.filter { it.kravtype == NYTT_KRAV }) +
                endreKravService.sendAllEndreKrav(kravList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }) +
                stoppKravService.sendAllStoppKrav(kravList.filter { it.kravtype == STOPP_KRAV })

        handleErrors(allResponses)

        return allResponses
    }

    private suspend fun updateSkeKravidentifikatorForEndringerAndStopp() {
        val unsentEndringerAndStopp = databaseService.getAllUnsentEndringerAndStopp()
        val requestResults = mutableListOf<RequestResult>()
        val slackErrorsHandled = mutableSetOf<String>()

        unsentEndringerAndStopp.forEach { krav ->
            // Sjekke om vi har det opprinnelige kravet i vår database
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(krav.referansenummerGammelSak)
            if (skeKravidentifikator.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(krav.saksnummerNAV, skeKravidentifikator)
                return@forEach
            }

            val requestResult = getKravidentifikatorFromSkatt(krav)

            // Feil som 403, 500 osv skal håndteres som normalt
            if (requestResult.status != Status.HTTP404_FANT_IKKE_SAKSREF) requestResults.add(requestResult)

            // Vi fant kravidentifikator fra SKE
            if (requestResult.kravidentifikator.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(krav.saksnummerNAV, requestResult.kravidentifikator)
            } else {
                databaseService.updateStatus(requestResult.status.value, krav.corrId)

                // Fra SKE vil vi få feilmeldingen "innkrevingsoppdrag eksisterer ikke" men vi ønsker mer tydelig informasjon samt informasjonen vi trenger for å kunne følge det opp manuelt.
                if (requestResult.status == Status.HTTP404_FANT_IKKE_SAKSREF) {
                    handle404FromAvstemming(requestResult, krav, slackErrorsHandled)
                }
            }
        }
        handleErrors(requestResults)
    }

    private suspend fun handle404FromAvstemming(
        requestResult: RequestResult,
        krav: Krav,
        slackErrorsHandled: MutableSet<String>,
    ) {
        val shouldAlert = slackErrorsHandled.add(krav.saksnummerNAV)

        handleError(
            requestResult,
            FeilResponse(
                type = requestResult.feilResponse?.type ?: KRAV_EKSISTERER_IKKE,
                title = "Fant ikke gyldig kravidentifikator",
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
            if (responseStatus == HttpStatusCode.OK) {
                if (kravidentifikator.isEmpty()) {
                    Status.UKJENT_FEIL
                } else {
                    Status.entries.firstOrNull { it.value == krav.status } ?: Status.UKJENT_FEIL
                }
            } else {
                definertStatus.first
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

    private suspend fun handleError(
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

    private suspend fun handleErrors(responses: List<RequestResult>) {
        responses
            .filterNot { it.httpStatusCode.isSuccess() }
            .forEach { result ->
                handleError(result, result.feilResponse)
            }
    }

    private suspend fun saveErrorMessage(
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

        dataSource.asyncTransaction { session ->
            val feilmelding =
                Feilmelding(
                    0L,
                    KravRepository.getKravTableIdFromCorrelationId(session, krav.corrId),
                    krav.corrId,
                    krav.saksnummerNAV,
                    skeKravidentifikator,
                    resolvedFeilResponse.status.toString(),
                    resolvedFeilResponse.detail,
                    request,
                    response,
                    LocalDateTime.now(),
                )
            FeilmeldingRepository.insertFeilmeldinger(session, listOf(feilmelding))
        }
    }

    suspend fun checkKravDateForAlert() {
        databaseService
            .getAllKravForStatusCheck()
            .filter { it.tidspunktSendt?.isBefore((LocalDateTime.now().minusHours(24))) == true }
            .also {
                if (it.isNotEmpty()) logger.info { "Krav med saksnummer ${it.joinToString { krav -> krav.saksnummerNAV }} har blitt forsøkt resendt i over én dag" }
            }.forEach {
                slackService.addError(
                    it.filnavn,
                    "Krav har blitt forsøkt resendt for lenge",
                    Pair(
                        "Krav har blitt forsøkt resendt i over 24t",
                        "Krav med saksnummer ${it.saksnummerNAV} har blitt forsøkt resendt i ${Duration.between(it.tidspunktSendt, LocalDateTime.now()).toDays()} dager.\n" +
                            "Kravet har status ${it.status} og ble originalt sendt ${it.tidspunktSendt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}",
                    ),
                )
            }
        slackService.sendErrors()
    }

    private fun logResult(result: List<RequestResult>) {
        result.partition { it.httpStatusCode.isSuccess() }.also { (successful, unsuccessful) ->
            successful.aggregertPerFil().forEach { (filnavn, telling) ->
                logger.info { "Fil: $filnavn - Nye: ${telling.antallNye}, Endringer: ${telling.antallEndringer}, Stopp: ${telling.antallStopp}" }
            }
            unsuccessful.aggregertPerFil().forEach { (filnavn, telling) ->
                logger.info { "Ikke vellykkede - Fil: $filnavn - Nye: ${telling.antallNye}, Endringer: ${telling.antallEndringer}, Stopp: ${telling.antallStopp}" }
            }
            logger.info { "Sendte ${result.size} krav${if (unsuccessful.isNotEmpty()) ". $unsuccessful feilet" else ""}" }
        }
    }
}

data class Telling(
    val antallNye: Int,
    val antallEndringer: Int,
    val antallStopp: Int,
)

fun List<RequestResult>.aggregertPerFil(): Map<String, Telling> =
    groupBy { it.krav.filnavn }
        .mapValues { (_, resultaterPerFil) ->
            val antallNye = resultaterPerFil.count { it.krav.kravtype == NYTT_KRAV }
            val antallEndringer = resultaterPerFil.count { it.krav.kravtype == ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }
            val antallStopp = resultaterPerFil.count { it.krav.kravtype == STOPP_KRAV }

            Telling(antallNye, antallEndringer, antallStopp)
        }
