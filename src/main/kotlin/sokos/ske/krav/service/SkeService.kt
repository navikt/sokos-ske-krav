package sokos.ske.krav.service

import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackService
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.isOpprettKrav
import sokos.ske.krav.util.parseTo
import sokos.ske.krav.validation.LineValidator
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val statusService: StatusService = StatusService(skeClient, databaseService),
    private val stoppKravService: StoppKravService = StoppKravService(skeClient, databaseService),
    private val endreKravService: EndreKravService = EndreKravService(skeClient, databaseService),
    private val opprettKravService: OpprettKravService = OpprettKravService(skeClient, databaseService),
    private val slackService: SlackService = SlackService(),
    private val ftpService: FtpService = FtpService(),
) {
    private var haltRun = false

    suspend fun checkKravDateForAlert() {
        println("Checking krav for alert")
        val kravListe = databaseService.getAllKravForStatusCheck()
        println("antall krav for status sjekk: ${kravListe.size}")

        kravListe
            .filter { it.tidspunktSendt?.isBefore((LocalDateTime.now().minusHours(24))) == true }
            .also { secureLogger.info { "Krav med saksnummer ${it.joinToString { it.saksnummerNAV }} har blitt forsøkt resendt i over én dag" } }
            .forEach {
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

    suspend fun handleNewKrav() {
        if (haltRun) {
            secureLogger.info("*** Kjøring er blokkert ***")
            return
        }

        resendKrav()
        sendNewFilesToSKE()
        delay(5000)
        resendKrav()

        slackService.sendErrors()

        if (haltRun) {
            haltRun = false
            secureLogger.info("*** Kjøring er ublokkert ***")
        }
    }

    private suspend fun resendKrav() {
        statusService.getMottaksStatus()
        databaseService.getAllKravForResending().takeIf { it.isNotEmpty() }?.let {
            secureLogger.info("Resender ${it.size} krav")
            Metrics.numberOfKravResent.increment(sendKrav(it).size.toDouble())
        }
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        if (files.isNotEmpty()) {
            val filtekst = if (files.size == 1) "fil" else "filer"
            val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
            secureLogger.info("*** Starter sending av ${files.size} $filtekst $datetime***")
        } else {
            secureLogger.info("*** Ingen nye filer ***")
        }

        files.forEach { file ->
            processFile(file)
            sendKrav(databaseService.getAllUnsentKrav()).also { logResult(it) }
        }
    }

    private suspend fun processFile(file: FtpFil) {
        secureLogger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
        val validatedLines = LineValidator().validateNewLines(file, databaseService)

        handleValidationResults(file, validatedLines)

        databaseService.saveAllNewKrav(validatedLines, file.name)
        ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)

        updateAllEndringerAndStopp(file.name, validatedLines.filterNot { it.isOpprettKrav() })
    }

    private suspend fun sendKrav(kravTableList: List<KravTable>): List<RequestResult> {
        if (kravTableList.isNotEmpty()) secureLogger.info("Sender ${kravTableList.size}")

        val allResponses =
            opprettKravService.sendAllOpprettKrav(kravTableList.filter { it.kravtype == NYTT_KRAV }) +
                endreKravService.sendAllEndreKrav(kravTableList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }) +
                stoppKravService.sendAllStoppKrav(kravTableList.filter { it.kravtype == STOPP_KRAV })

        handleErrors(allResponses, databaseService)

        return allResponses
    }

    private suspend fun updateAllEndringerAndStopp(
        fileName: String,
        kravLinjer: List<KravLinje>,
    ) {
        kravLinjer.forEach { krav ->
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(krav.referansenummerGammelSak)
            var skeKravidentifikatorSomSkalLagres = skeKravidentifikator

            if (skeKravidentifikator.isBlank()) {
                val httpResponse = skeClient.getSkeKravidentifikator(krav.referansenummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    skeKravidentifikatorSomSkalLagres = httpResponse.parseTo<AvstemmingResponse>()?.kravidentifikator ?: ""
                }
            }

            if (skeKravidentifikatorSomSkalLagres.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(krav.saksnummerNav, skeKravidentifikatorSomSkalLagres)
            } else {
                slackService.addError(
                    fileName,
                    "Fant ikke gyldig kravidentifikator for migrert krav",
                    Pair(
                        "Fant ikke gyldig kravidentifikator for migrert krav",
                        "Saksnummer: ${krav.saksnummerNav} \n ReferansenummerGammelSak: ${krav.referansenummerGammelSak} \n Dette må følges opp manuelt",
                    ),
                )
                secureLogger.error { "Fant ikke gyldig kravidentifikator for migrert krav:  ${krav.referansenummerGammelSak} " }
            }
        }
    }

    private fun handleValidationResults(
        file: FtpFil,
        validatedLines: List<KravLinje>,
    ) {
        if (file.kravLinjer.size > validatedLines.size) {
            secureLogger.warn("Ved validering av linjer i fil ${file.name} har ${file.kravLinjer.size - validatedLines.size} linjer velideringsfeil ")
        }
        if (validatedLines.size >= 1000) {
            secureLogger.info("***Stor fil. Blokkerer kjøring***")
            haltRun = true
        }
    }

    private suspend fun handleErrors(
        responses: List<RequestResult>,
        databaseService: DatabaseService,
    ) {
        responses
            .filterNot { it.response.status.isSuccess() }
            .forEach { result ->
                databaseService.saveErrorMessage(
                    result.request,
                    result.response,
                    result.kravTable,
                    result.kravidentifikator,
                )
                result.response.parseTo<FeilResponse>()?.let { feilResponse ->
                    val errorPair = Pair(feilResponse.title, feilResponse.detail)
                    slackService.addError(result.kravTable.filnavn, "Feil fra SKE", errorPair)
                }
            }
    }

    private fun logResult(result: List<RequestResult>) {
        val successful = result.filter { it.response.status.isSuccess() }
        val unsuccessful = result.size - successful.size
        secureLogger.info { "Sendte ${result.size} krav${if (unsuccessful > 0) ". $unsuccessful feilet" else ""}" }

        val nye = successful.count { it.kravTable.kravtype == NYTT_KRAV }
        val endringer = successful.count { it.kravTable.kravtype == ENDRING_RENTE } + successful.count { it.kravTable.kravtype == ENDRING_HOVEDSTOL }
        val stopp = successful.count { it.kravTable.kravtype == STOPP_KRAV }
        secureLogger.info { "$nye nye, $endringer endringer, $stopp stopp" }
    }
}
