package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.Status.Companion.isOkStatus
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
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
    private val logger = KotlinLogging.logger {}

    suspend fun handleNewKrav() {
        statusService.hentOgOppdaterMottaksStatus()
        resendKrav()

        sendNewFilesToSKE()

        delay(10_000)
        statusService.hentOgOppdaterMottaksStatus()
        resendKrav()
    }

    private suspend fun sendNewFilesToSKE() {
        val files = ftpService.getValidatedFiles()
        logger.info("*******************${LocalDateTime.now()}*******************")
        logger.info("Starter innsending av ${files.size} filer")

        files.map { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
            val result = sendKrav(file)
            AlarmService.handleFeil(result, file)

            result
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        logger.info("*******************KJØRING FERDIG*******************")
    }

    private suspend fun sendKrav(
        file: FtpFil,
    ): List<RequestResult> {

        val validatedLines = LineValidator.validateNewLines(file)
        databaseService.saveAllNewKrav(validatedLines)
        updateAllEndringerAndStopp(validatedLines.filter { !it.isOpprettKrav() })

        val kravLinjer = databaseService.getAllUnsentKrav()

        val requestResults = mutableListOf<RequestResult>()
        val allResponses = mutableListOf<RequestResult>()


        allResponses.addAll(
            stoppKravService.sendAllStoppKrav(kravLinjer.filter { it.kravtype == STOPP_KRAV })
        )
        allResponses.addAll(
            endreKravService.sendAllEndreKrav(kravLinjer.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE })
        )
        allResponses.addAll(
            opprettKravService.sendAllOpprettKrav(kravLinjer.filter { it.kravtype == NYTT_KRAV })
        )

        Metrics.numberOfKravRead.inc()

        allResponses.filter { !it.response.status.isSuccess() }
            .forEach() {
                databaseService.saveErrorMessage(
                    it.request,
                    it.response,
                    it.krav,
                    it.kravidentifikator,
                )
            }

        return allResponses
    }

    private suspend fun resendKrav(): List<RequestResult> {
        val kravSomSkalResendes = databaseService.getAllKravForResending()

        val feilListe = mutableListOf<RequestResult>()

        val stoppKrav = stoppKravService.sendAllStoppKrav(kravSomSkalResendes.filter { it.kravtype == STOPP_KRAV })
        feilListe.addAll(stoppKrav)

        val endreKrav =
            endreKravService.sendAllEndreKrav(kravSomSkalResendes.filter { it.kravtype == ENDRING_RENTE || it.kravtype == ENDRING_HOVEDSTOL })
        feilListe.addAll(endreKrav)

        val opprettKrav = opprettKravService.sendAllOpprettKrav(kravSomSkalResendes.filter { it.kravtype == NYTT_KRAV })
        feilListe.addAll(opprettKrav)

        return feilListe.filter { !it.status.isOkStatus() }
    }

    private suspend fun updateAllEndringerAndStopp(kravLinjer: List<KravLinje>) =
        kravLinjer.forEach {
            val skeKravidentifikator = databaseService.getSkeKravidentifikator(it.referanseNummerGammelSak)
            var skeKravidentifikatorSomSkalLagres = skeKravidentifikator
            if (skeKravidentifikator.isBlank()) {
                val httpResponse = skeClient.getSkeKravidentifikator(it.referanseNummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    skeKravidentifikatorSomSkalLagres = httpResponse.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                }
            }
            if (skeKravidentifikatorSomSkalLagres.isNotBlank()) databaseService.updateEndringWithSkeKravIdentifikator(
                it.saksNummer,
                skeKravidentifikatorSomSkalLagres
            )
        }

}
