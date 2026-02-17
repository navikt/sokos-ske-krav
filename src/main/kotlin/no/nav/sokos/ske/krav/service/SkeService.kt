package no.nav.sokos.ske.krav.service

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlinx.coroutines.delay

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PostgresConfig
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
import no.nav.sokos.ske.krav.util.defineStatusWithError
import no.nav.sokos.ske.krav.util.parseTo
import no.nav.sokos.ske.krav.validation.LineValidator

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

private val logger = mu.KotlinLogging.logger {}

class SkeService(
    private val dataSource: HikariDataSource = PostgresConfig.dataSource,
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
        delay(5000)
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
        if (files.isNotEmpty()) {
            val filtekst = if (files.size == 1) "fil" else "filer"
            val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
            logger.info("*** Starter sending av ${files.size} $filtekst $datetime***")
        } else {
            logger.info("*** Ingen nye filer ***")
        }

        files.forEach { file ->
            processFile(file)
            sendKrav(databaseService.getAllUnsentKrav()).also { logResult(it) }
        }
    }

    private suspend fun processFile(file: FtpFil) {
        logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
        val validatedLines = LineValidator().validateNewLines(file, databaseService)

        handleValidationResults(file, validatedLines)

        databaseService.saveAllNewKrav(validatedLines, file.name)
        ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)

        updateSkeKravidentifikatorForEndringerAndStopp()
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

    /*
     * Når vi sender inn endringer til SKE så vil de at vi skal sende inn kravidentifikatoren deres for det opprinnelige kravet, eller det OPPRINNELIGE Nav saksnummeret
     * Dersom det opprinnelige kravet har gått gjennom denne applikasjonen så er alt OK
     * Hvis ikke, så må vi spørre mot skatteetatens avstemmingAPI
     * Skatteetaten "migrerte" krav for ett år tilbake i tid da denne applikasjonen gikk i produksjon, og disse kravene kan vi finne via avstemmmingAPIet
     * Men noen ganger får vi inn en endring på en endring av et krav som ikke finnes i vårt system og som skatteetaten ikke kan finne frem til
     * Dette er pga måten skatteetaten bruker saksnummer vs hvordan Nav bruker saksnummer
     * Når det skjer så kaller vi det "dobbel endring på migrert krav" og det må håndteres manuelt
     *
     * Denne funksjonen leter i vår database, så spør den avstemmingAPI til SKE, og dersom de ikke har det så sendes en alarm til slack
     * */
    private suspend fun updateSkeKravidentifikatorForEndringerAndStopp() {
        val krav = databaseService.getAllUnsentEndringerAndStopp()
        val requestResults = mutableListOf<RequestResult>()
        val slackErrorsHandled = mutableListOf<String>()

        krav.forEach { krav ->
            // Sjekke om vi har det opprinnelige kravet i vår database
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(krav.referansenummerGammelSak)
            if (skeKravidentifikator.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(krav.saksnummerNAV, skeKravidentifikator)
                return@forEach
            }

            // Spørre mot skatteetatens avstemmingendepunkt
            val requestResult = getKravidentifikatorFromSkatt(krav)

            // Feil som 403, 500 osv skal håndteres som normalt
            if (requestResult.status != Status.HTTP404_FANT_IKKE_SAKSREF) requestResults.add(requestResult)

            if (requestResult.kravidentifikator.isNotBlank()) {
                // Oppdatere raden i database med kravidentifikatoren vi fant
                databaseService.updateEndringWithSkeKravIdentifikator(krav.saksnummerNAV, requestResult.kravidentifikator)
            } else {
                databaseService.updateStatus(requestResult.status.value, krav.corrId)

                // Tidlig return siden endringer er to requests og vi vil ikke ha to identiske alarmer for det samme saksnummeret
                if (slackErrorsHandled.contains(krav.saksnummerNAV)) return@forEach

                // Fra SKE vil vi få feilmeldingen "innkrevingsoppdrag eksisterer ikke" men vi ønsker mer tydelig informasjon samt informasjonen vi trenger for å kunne følge det opp manuelt
                if (requestResult.status == Status.HTTP404_FANT_IKKE_SAKSREF) {
                    // Use the feilResponse that was already parsed in getKravidentifikatorFromSkatt
                    val feilResponse = requestResult.feilResponse
                    val (type, instance) =
                        if (feilResponse != null) {
                            Pair(feilResponse.type, feilResponse.instance)
                        } else {
                            // Unexpected case where feilResponse is null for a 404 - use defaults
                            logger.error { "Unexpected null feilResponse for HTTP404_FANT_IKKE_SAKSREF. Saksnummer: ${krav.saksnummerNAV}, ReferansenummerGammelSak: ${krav.referansenummerGammelSak}" }
                            Pair(KRAV_EKSISTERER_IKKE, "unknown")
                        }

                    handleError(
                        requestResult,
                        FeilResponse(
                            type = type,
                            title = FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENT,
                            status = requestResult.response.status.value,
                            detail = "Saksnummer: ${krav.saksnummerNAV} \n ReferansenummerGammelSak: ${krav.referansenummerGammelSak} \n Dette må følges opp manuelt",
                            instance = instance,
                        ),
                    )
                    if (feilResponse != null) {
                        logger.warn { "Fant ikke gyldig kravidentifikator for ReferansenummerGammelSak med referansenummerGammelSak: ${requestResult.krav.referansenummerGammelSak} " }
                    }
                    slackErrorsHandled.add(krav.saksnummerNAV)
                }
            }
        }
        handleErrors(requestResults)
    }

    private suspend fun getKravidentifikatorFromSkatt(krav: Krav): RequestResult {
        val responseAvstemmingSkatt = skeClient.getSkeKravidentifikator(krav.referansenummerGammelSak)
        val (status, feilResponse) = defineStatusWithError(responseAvstemmingSkatt)

        return RequestResult(
            response = responseAvstemmingSkatt,
            request = krav.referansenummerGammelSak,
            krav = krav,
            kravidentifikator = responseAvstemmingSkatt.parseTo<AvstemmingResponse>()?.kravidentifikator ?: "",
            status = status,
            feilResponse = feilResponse,
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
    ) {
        // Dette sørger for at HTTP feil ikke blir sendt til slack. Kun feil fra SKE blir sendt.
        feilResponse?.let {
            val errorPair = Pair(feilResponse.title, feilResponse.detail)
            slackService.addError(requestResult.krav.filnavn, slackService.feilFraSkeHeader, errorPair)
        }

        saveErrorMessage(
            requestResult.request,
            requestResult.response,
            requestResult.krav,
            requestResult.kravidentifikator,
        )
    }

    private suspend fun handleErrors(responses: List<RequestResult>) {
        responses
            .filterNot { it.response.status.isSuccess() }
            .forEach { result ->
                handleError(result, result.response.parseTo<FeilResponse>())
            }
    }

    private suspend fun saveErrorMessage(
        request: String,
        response: HttpResponse,
        krav: Krav,
        kravidentifikator: String,
    ) {
        val skeKravidentifikator =
            if (kravidentifikator == krav.saksnummerNAV || kravidentifikator == krav.referansenummerGammelSak) "" else kravidentifikator

        val responseAsText = response.bodyAsText()
        val feilResponse = response.parseTo<FeilResponse>() ?: FeilResponse("egendefinert", "Feil i parsing av http respons", response.status.value, responseAsText, "")

        dataSource.asyncTransaction { session ->
            val feilmelding =
                Feilmelding(
                    0L,
                    KravRepository.getKravTableIdFromCorrelationId(session, krav.corrId),
                    krav.corrId,
                    krav.saksnummerNAV,
                    skeKravidentifikator,
                    feilResponse.status.toString(),
                    feilResponse.detail,
                    request,
                    responseAsText,
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
        val successful = result.filter { it.response.status.isSuccess() }
        val unsuccessful = result.size - successful.size
        logger.info { "Sendte ${result.size} krav${if (unsuccessful > 0) ". $unsuccessful feilet" else ""}" }

        val nye = successful.count { it.krav.kravtype == NYTT_KRAV }
        val endringer = successful.count { it.krav.kravtype == ENDRING_RENTE } + successful.count { it.krav.kravtype == ENDRING_HOVEDSTOL }
        val stopp = successful.count { it.krav.kravtype == STOPP_KRAV }
        logger.info { "$nye nye, $endringer endringer, $stopp stopp" }
    }
}
