package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.isOpprettKrav
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
    private val logger = KotlinLogging.logger("secureLogger")

    private var haltRun = false

    suspend fun handleNewKrav() {
        if (haltRun) {
            logger.info("*** Run is halted ***")
            return
        }

        statusService.hentOgOppdaterMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())

        sendNewFilesToSKE()

        statusService.hentOgOppdaterMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())

        if (haltRun) {
            haltRun = false
            logger.info("*** Run has been unhalted ***")
        }
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        if (files.isNotEmpty()) {
            logger.info("*** Starter sending av ${files.size} filer ${LocalDate.now()} ***")
        } else {
            logger.info("*** Ingen nye filer ***")
        }

        files.forEach { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")

            val validatedLines = LineValidator().validateNewLines(file, databaseService)

            if (file.kravLinjer.size > validatedLines.size) {
                logger.warn("Ved validering av linjer i fil ${file.name} har ${file.kravLinjer.size - validatedLines.size} linjer velideringsfeil ")
            }
            if (validatedLines.size >= 1000) {
                logger.info("***Large file. Halting run***")
                haltRun = true
            }
            databaseService.saveAllNewKrav(validatedLines, file.name)
            ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)

            updateAllEndringerAndStopp(validatedLines.filter { !it.isOpprettKrav() })

            val result = sendKrav(databaseService.getAllUnsentKrav())
            logResult(result)
        }
    }

    private fun logResult(result: List<RequestResult>) {
        val successful = result.filter { it.response.status.isSuccess() }
        val unsuccessful = result.size - successful.size
        val unsuccesfulMessage = if (unsuccessful > 0) ". $unsuccessful feilet" else ""
        logger.info { "Sendte ${result.size} krav$unsuccesfulMessage" }

        val nye = successful.count { it.kravTable.kravtype == NYTT_KRAV }
        val endringer = successful.count { it.kravTable.kravtype == ENDRING_RENTE } + successful.count { it.kravTable.kravtype == ENDRING_HOVEDSTOL }
        val stopp = successful.count { it.kravTable.kravtype == STOPP_KRAV }
        logger.info { "$nye nye, $endringer endringer, $stopp stopp" }
    }

    private suspend fun sendKrav(kravTableList: List<KravTable>): List<RequestResult> {
        if (kravTableList.isNotEmpty()) logger.info("Sender ${kravTableList.size}")

        val allResponses = mutableListOf<RequestResult>()
        allResponses.addAll(
            opprettKravService.sendAllOpprettKrav(kravTableList.filter { it.kravtype == NYTT_KRAV }),
        )
        allResponses.addAll(
            endreKravService.sendAllEndreKrav(kravTableList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }),
        )
        allResponses.addAll(
            stoppKravService.sendAllStoppKrav(kravTableList.filter { it.kravtype == STOPP_KRAV }),
        )

        if (kravTableList.isNotEmpty()) logger.info("Alle krav sendt, lagrer eventuelle feilmeldinger")

        val feilmeldinger = mutableListOf<Pair<String, String>>()
        allResponses
            .filter { !it.response.status.isSuccess() }
            .map {
                databaseService.saveErrorMessage(
                    it.request,
                    it.response,
                    it.kravTable,
                    it.kravidentifikator,
                )
                logger.warn("Feilmeldinger fra sending av krav: ${it.response.status} - ${it.response.body<FeilResponse>().title} - ${it.response.body<FeilResponse>().detail}")
                feilmeldinger.add(Pair(it.response.body<FeilResponse>().title, it.response.body<FeilResponse>().detail))
            }
        if (feilmeldinger.isNotEmpty()) slackClient.sendValideringsfeilFraSke(feilmeldinger)
        return allResponses
    }

    private suspend fun updateAllEndringerAndStopp(kravLinjer: List<KravLinje>) =
        kravLinjer.forEach {
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(it.referansenummerGammelSak)
            var skeKravidentifikatorSomSkalLagres = skeKravidentifikator
            if (skeKravidentifikator.isBlank()) {
                val httpResponse = skeClient.getSkeKravidentifikator(it.referansenummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    skeKravidentifikatorSomSkalLagres = httpResponse.body<AvstemmingResponse>().kravidentifikator
                }
            }

            if (skeKravidentifikatorSomSkalLagres.isNotBlank()) {
                databaseService.updateEndringWithSkeKravIdentifikator(
                    it.saksnummerNav,
                    skeKravidentifikatorSomSkalLagres,
                )
            } else {
                val melding =
                    Pair(
                        "Fant ikke gyldig kravidentifikator for migrert krav",
                        "Saksnummer: ${it.saksnummerNav} \n ReferansenummerGammelSak: ${it.referansenummerGammelSak} \n Dette må følges opp manuelt",
                    )
                SlackClient().sendFantIkkeKravidentifikator(
                    meldinger = listOf(melding),
                )
            }
        }
}
