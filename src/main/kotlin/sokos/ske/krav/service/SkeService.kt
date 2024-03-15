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
import sokos.ske.krav.util.isNyttKrav
import sokos.ske.krav.validation.LineValidator
import java.time.LocalDateTime


const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_RENTER = "ENDRE_RENTER"
const val ENDRE_HOVEDSTOL = "ENDRE_HOVEDSTOL"
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

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles()

    suspend fun handleNewKrav() {

        statusService.hentOgOppdaterMottaksStatus()
        resendIkkeReskontroforteKrav()

        sendNewFilesToSKE()

        delay(10_000)
        statusService.hentOgOppdaterMottaksStatus()
        resendIkkeReskontroforteKrav()

    }

    suspend fun sendNewFilesToSKE(): List<RequestResult> {
        val files = ftpService.getValidatedFiles()
        logger.info("*******************${LocalDateTime.now()}*******************")
        logger.info("Starter innsending av ${files.size} filer")

        val results = files.map { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
            val result = sendKrav(file)
            AlarmService.handleFeil(result, file)

            result
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        logger.info("*******************KJØRING FERDIG*******************")
        return results.flatten()
    }

    private suspend fun sendKrav(
        file: FtpFil,
    ): List<RequestResult> {

        val linjer = LineValidator.getOkLines(file)

        lagreOgOppdaterAlleNyeKrav(linjer)

        val kravLinjer = databaseService.hentAlleKravSomIkkeErSendt()

        val requestResults = mutableListOf<Map<String, RequestResult>>()
        val allResponses = mutableListOf<RequestResult>()


        requestResults.addAll(
            stoppKravService.sendAllStopKrav(kravLinjer.filter { it.kravtype == STOPP_KRAV })
        )
        requestResults.addAll(
            endreKravService.sendAllEndreKrav(kravLinjer.filter { it.kravtype == ENDRE_HOVEDSTOL || it.kravtype == ENDRE_RENTER })
        )
        requestResults.addAll(
            opprettKravService.sendAllOpprettKrav(kravLinjer.filter { it.kravtype == NYTT_KRAV })
        )

        Metrics.numberOfKravRead.inc()

        allResponses.addAll(requestResults.flatMap { it.values })

        allResponses.filter { !it.response.status.isSuccess() }
            .forEach() {
                databaseService.saveErrorMessageToDatabase(
                    it.request,
                    it.response,
                    it.krav,
                    it.kravIdentifikator,
                    it.corrId
                )
            }

        return allResponses
    }

    suspend fun resendIkkeReskontroforteKrav(): Map<String, RequestResult> {
        val kravSomSkalResendes = databaseService.hentKravSomSkalResendes()

        val feilListe = mutableMapOf<String, RequestResult>()

        val stoppKrav = stoppKravService.sendAllStopKrav(kravSomSkalResendes.filter { it.kravtype == STOPP_KRAV })
        feilListe.putAll(stoppKrav.flatMap { it.entries }.associate { it.key to it.value })

        val endreKrav =
            endreKravService.sendAllEndreKrav(kravSomSkalResendes.filter { it.kravtype == ENDRE_RENTER || it.kravtype == ENDRE_HOVEDSTOL })
        feilListe.putAll(endreKrav.flatMap { it.entries }.associate { it.key to it.value })

        val opprettKrav = opprettKravService.sendAllOpprettKrav(kravSomSkalResendes.filter { it.kravtype == NYTT_KRAV })
        feilListe.putAll(opprettKrav.flatMap { it.entries }.associate { it.key to it.value })

        return feilListe.filter { !it.value.status.isOkStatus() }
    }

    private suspend fun lagreOgOppdaterAlleNyeKrav(kravLinjer: List<KravLinje>) {
        databaseService.saveAllNewKrav(kravLinjer)

        kravLinjer.filter { !it.isNyttKrav() }.map {
            val skeKravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
            var skeKravidenLagres = skeKravident
            if (skeKravident.isBlank()) {
                val httpResponse = skeClient.getSkeKravident(it.referanseNummerGammelSak)
                if (httpResponse.status.isSuccess()) {
                    skeKravidenLagres = httpResponse.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                }
            }
            if (skeKravidenLagres.isNotBlank()) databaseService.updateSkeKravidentifikator(
                it.saksNummer,
                skeKravidenLagres
            )
        }
    }
}
