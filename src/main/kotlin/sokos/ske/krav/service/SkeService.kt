package sokos.ske.krav.service

import io.ktor.http.isSuccess
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
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
import java.time.LocalDate

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
    private val ftpService: FtpService = FtpService(),
    private val slackClient: SlackClient = SlackClient(),
) {
    private var haltRun = false

    suspend fun handleNewKrav() {
        if (haltRun) {
            secureLogger.info("*** Run is halted ***")
            return
        }

        statusService.getMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())

        sendNewFilesToSKE()

        statusService.getMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())

        if (haltRun) {
            haltRun = false
            secureLogger.info("*** Run has been unhalted ***")
        }
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        if (files.isNotEmpty()) {
            secureLogger.info("*** Starter sending av ${files.size} filer ${LocalDate.now()} ***")
        } else {
            secureLogger.info("*** Ingen nye filer ***")
        }

        files.forEach { file ->
            secureLogger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")

            val validatedLines = LineValidator().validateNewLines(file, databaseService)

            if (file.kravLinjer.size > validatedLines.size) {
                secureLogger.warn("Ved validering av linjer i fil ${file.name} har ${file.kravLinjer.size - validatedLines.size} linjer velideringsfeil ")
            }
            if (validatedLines.size >= 1000) {
                secureLogger.info("***Large file. Halting run***")
                haltRun = true
            }
            databaseService.saveAllNewKrav(validatedLines, file.name)
            ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)

            updateAllEndringerAndStopp(file.name, validatedLines.filter { !it.isOpprettKrav() })

            val result = sendKrav(databaseService.getAllUnsentKrav())
            logResult(result)
        }
    }

    private suspend fun sendKrav(kravTableList: List<KravTable>): List<RequestResult> {
        if (kravTableList.isNotEmpty()) secureLogger.info("Sender ${kravTableList.size}")

        val allResponses =
            opprettKravService.sendAllOpprettKrav(kravTableList.filter { it.kravtype == NYTT_KRAV }) +
                endreKravService.sendAllEndreKrav(kravTableList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }) +
                stoppKravService.sendAllStoppKrav(kravTableList.filter { it.kravtype == STOPP_KRAV })

        if (kravTableList.isNotEmpty()) secureLogger.info("Alle krav sendt, lagrer eventuelle feilmeldinger")

        val feil = mutableMapOf<String, MutableList<Pair<String, String>>>()

        allResponses
            .filter { !it.response.status.isSuccess() }
            .forEach {
                databaseService.saveErrorMessage(
                    it.request,
                    it.response,
                    it.kravTable,
                    it.kravidentifikator,
                )

                it.response.parseTo<FeilResponse>()?.let { feilResponse ->
                    val errorPair = Pair(feilResponse.title, feilResponse.detail)
                    feil.putIfAbsent(it.kravTable.filnavn, mutableListOf(errorPair))?.add(errorPair)
                }
            }

        feil.forEach { (fileName, messages) ->
            slackClient.sendMessage("Feil fra SKE", fileName, messages)
        }

        return allResponses
    }

    private suspend fun updateAllEndringerAndStopp(
        fileName: String,
        kravLinjer: List<KravLinje>,
    ) {
        val feilmeldinger = mutableListOf<Pair<String, String>>()
        kravLinjer.forEach { krav ->
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(krav.referansenummerGammelSak)
            var skeKravidentifikatorSomSkalLagres = skeKravidentifikator

            if (skeKravidentifikator.isBlank()) {
                val httpResponse = skeClient.getSkeKravidentifikator(krav.referansenummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    httpResponse.parseTo<AvstemmingResponse>()?.let {
                        skeKravidentifikatorSomSkalLagres = it.kravidentifikator
                    }
                }
            }

            if (skeKravidentifikatorSomSkalLagres.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(
                    krav.saksnummerNav,
                    skeKravidentifikatorSomSkalLagres,
                )
            } else {
                val melding =
                    Pair(
                        "Fant ikke gyldig kravidentifikator for migrert krav",
                        "Saksnummer: ${krav.saksnummerNav} \n ReferansenummerGammelSak: ${krav.referansenummerGammelSak} \n Dette må følges opp manuelt",
                    )
                feilmeldinger.add(melding)
            }
        }

        if (feilmeldinger.isNotEmpty()) {
            slackClient.sendMessage(
                "Fant ikke kravidentifikator for migrert krav",
                fileName,
                feilmeldinger,
            )
        }
    }

    private fun logResult(result: List<RequestResult>) {
        val successful = result.filter { it.response.status.isSuccess() }
        val unsuccessful = result.size - successful.size
        val unsuccesfulMessage = if (unsuccessful > 0) ". $unsuccessful feilet" else ""
        secureLogger.info { "Sendte ${result.size} krav$unsuccesfulMessage" }

        val nye = successful.count { it.kravTable.kravtype == NYTT_KRAV }
        val endringer = successful.count { it.kravTable.kravtype == ENDRING_RENTE } + successful.count { it.kravTable.kravtype == ENDRING_HOVEDSTOL }
        val stopp = successful.count { it.kravTable.kravtype == STOPP_KRAV }
        secureLogger.info { "$nye nye, $endringer endringer, $stopp stopp" }
    }
}
