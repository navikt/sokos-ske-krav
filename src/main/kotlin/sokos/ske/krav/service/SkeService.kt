package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.isOpprettKrav
import sokos.ske.krav.validation.LineValidator
import java.time.LocalDateTime

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
    private val skeClient: SkeClient,
    private val stoppKravService: StoppKravService,
    private val endreKravService: EndreKravService,
    private val opprettKravService: OpprettKravService,
    private val statusService: StatusService,
    private val databaseService: DatabaseService,
    private val ftpService: FtpService = FtpService(),
) {
    private val logger = KotlinLogging.logger("secureLogger")

    suspend fun handleNewKrav() {
        statusService.hentOgOppdaterMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())

        sendNewFilesToSKE().also { delay(10_000) }

        statusService.hentOgOppdaterMottaksStatus()
        Metrics.numberOfKravResent.increment(sendKrav(databaseService.getAllKravForResending()).size.toDouble())
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        logger.info("*******************${LocalDateTime.now()}*******************")
        logger.info("Starter innsending av ${files.size} filer")

        files.forEach { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")

            val validatedLines = LineValidator().validateNewLines(file, databaseService)
            Metrics.numberOfKravRead.increment(validatedLines.size.toDouble())

            databaseService.saveAllNewKrav(validatedLines, file.name)
            ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)

            updateAllEndringerAndStopp(validatedLines.filter { !it.isOpprettKrav() })

            val result = sendKrav(databaseService.getAllUnsentKrav())
            AlarmService.handleFeil(result, file)
        }
        logger.info("*******************KJØRING FERDIG*******************")
    }

    private suspend fun sendKrav(kravTableList: List<KravTable>): List<RequestResult> {
        logger.info("sender ${kravTableList.size}")

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

        allResponses
            .filter { !it.response.status.isSuccess() }
            .forEach {
                databaseService.saveErrorMessage(
                    it.request,
                    it.response,
                    it.kravTable,
                    it.kravidentifikator,
                )
            }

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
            }
        }
}
