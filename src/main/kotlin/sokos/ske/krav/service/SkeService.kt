package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.Status.Companion.isOkStatus
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.isNyttKrav
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
    private val alarmService: AlarmService,
    private val databaseService: DatabaseService = DatabaseService(),
    private val ftpService: FtpService = FtpService(),
) {
    private val logger = KotlinLogging.logger {}

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles()

    suspend fun handleNewKrav() {

        hentOgOppdaterMottaksStatus()
        resendIkkeReskontroforteKrav()

        sendNewFilesToSKE()

        delay(10_000)
        hentOgOppdaterMottaksStatus()
        val funkaIkke = resendIkkeReskontroforteKrav()
        alarmService.handleFeil(funkaIkke)
    }

    suspend fun sendNewFilesToSKE(): List<HttpResponse> {
        val files = ftpService.getValidatedFiles()
        logger.info("*******************${LocalDateTime.now()}*******************")
        logger.info("Starter innsending av ${files.size} filer")

        val responses = files.map { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")
            sendKrav(file)
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        logger.info("*******************KJØRING FERDIG*******************")
        return responses.flatten()
    }

    private suspend fun sendKrav(
        file: FtpFil,
    ): List<HttpResponse> {

        val linjer = file.kravLinjer.filter { LineValidator.validateLine(it, file.name) }

        lagreOgOppdaterAlleNyeKrav(linjer)

        val kravLinjer = databaseService.hentAlleKravSomIkkeErSendt()

        val requestResults = mutableListOf<Map<String, RequestResult>>()
        val allResponses = mutableListOf<RequestResult>()


        requestResults.addAll(
            stoppKravService.sendAllStopKrav(kravLinjer.filter { it.kravtype  == STOPP_KRAV }))
        requestResults.addAll(
            endreKravService.sendAllEndreKrav(kravLinjer.filter { it.kravtype  == ENDRE_HOVEDSTOL || it.kravtype == ENDRE_RENTER }))
        requestResults.addAll(
            opprettKravService.sendAllOpprettKrav(kravLinjer.filter { it.kravtype == NYTT_KRAV }))

        databaseService.updateSentKravToDatabase(requestResults)

        Metrics.numberOfKravRead.inc()

        allResponses.addAll(requestResults.flatMap { it.values })

        allResponses.filter { !it.response.status.isSuccess() }
            .forEach() {
                databaseService.saveErrorMessageToDatabase(
                    it.request,
                    it.response,
                    it.krav,
                    it.kravIdentifikator,
                    it.corrId,
                    file.name
                )
            }

        return emptyList()  //TODO TJA HVA DA
    }


    private fun handleHvaFGjorViNaa(krav: KravLinje) {
        logger.error(
            """
                        SAKSNUMMER: ${krav.saksNummer}
                        GAMMELT SAKSNUMMER: ${krav.referanseNummerGammelSak}
                        Hva F* gjør vi nå, dette skulle ikke skje
                    """
            // hva faen gjør vi nå??
            // Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
            //og de ikke kjenner igjen refnummer vi sender inn
        )
    }

    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        var antall = 0
        var feil = 0

        val start = Clock.System.now()
        val krav = databaseService.hentAlleKravSomIkkeErReskotrofort()
        println("antall krav som ikke er reskontroført: ${krav.size}")
        var tidSiste = Clock.System.now()
        val tidHentAlleKrav = (tidSiste - start).inWholeMilliseconds
        var tidHentMottakstatus = 0L
        var tidOppdaterstatus = 0L
        val result = krav.map {

            var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
            var kravIdentifikator = it.saksnummerSKE

            if (it.saksnummerSKE.isEmpty()) {
                kravIdentifikator = it.referanseNummerGammelSak
                kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR

                println("is empty, Kravident satt: $kravIdentifikator")
            }
            antall++
            val response = skeClient.getMottaksStatus(kravIdentifikator, kravIdentifikatorType)

            tidHentMottakstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
            tidSiste = Clock.System.now()

            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus, it.corr_id)
                    tidOppdaterstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
                    tidSiste = Clock.System.now()
                } catch (e: SerializationException) {
                    feil++
                    logger.error("Feil i dekoding av MottaksStatusResponse: ${e.message}")
                    throw e
                } catch (e: IllegalArgumentException) {
                    feil++
                    logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}")
                    throw e
                }
            } else {
                println(response.status)
            }
            "Status ok: ${response.status.value}, ${response.bodyAsText()}"
        }
        println("Antall krav hele greia: Antall behandlet  $antall, Antall feilet: $feil")
        println("tid for hele greia: ${(Clock.System.now() - start).inWholeMilliseconds}")
        println("Tid for å hente alle krav: ${tidHentAlleKrav}")
        println("Totalt tid for Henting av MOTTAKSTATUS: ${tidHentMottakstatus}")
        println("Totalt tid for Oppdatering av MOTTAKSTATUS: ${tidOppdaterstatus}")

        return result + "Antall behandlet  $antall, Antall feilet: $feil"
    }

    suspend fun hentValideringsfeil(): List<String> {
        val krav = databaseService.getAlleKravMedValideringsfeil()

        val resultat = krav.map {
            var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
            var kravIdentifikator = it.saksnummerSKE

            if (it.saksnummerSKE.isEmpty()) {
                kravIdentifikator = it.referanseNummerGammelSak
                kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
            }
            val response = skeClient.getValideringsfeil(kravIdentifikator, kravIdentifikatorType)

            if (response.status.isSuccess()) {
                val valideringsfeilResponse = response.body<ValideringsFeilResponse>()
                databaseService.saveValideringsfeil(valideringsfeilResponse, it.saksnummerSKE)
                "Status OK: ${response.bodyAsText()}"
            } else {
                "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
            }
        }
        if (resultat.isNotEmpty()) logger.info("HENTVALIDERINGSFEIL: Det er hentet valideringsfeil for ${resultat.size} krav")

        return resultat
    }

    suspend fun resendIkkeReskontroforteKrav(): Map<String, RequestResult> {
        val kravSomSkalResendes = databaseService.hentKravSomSkalResendes()

        val feilListe = mutableMapOf<String, RequestResult>()

        val stoppKrav = stoppKravService.sendAllStopKrav(kravSomSkalResendes.filter { it.kravtype == STOPP_KRAV })
        feilListe.putAll(stoppKrav.flatMap { it.entries }.associate { it.key to it.value })

        val endreKrav = endreKravService.sendAllEndreKrav(kravSomSkalResendes.filter { it.kravtype == ENDRE_RENTER || it.kravtype == ENDRE_HOVEDSTOL })
        feilListe.putAll(endreKrav.flatMap { it.entries }.associate { it.key to it.value })

        val opprettKrav = opprettKravService.sendAllOpprettKrav(kravSomSkalResendes.filter { it.kravtype == NYTT_KRAV })
        feilListe.putAll(opprettKrav.flatMap { it.entries }.associate { it.key to it.value })

        return feilListe.filter { !it.value.status.isOkStatus()}
    }

    suspend fun lagreOgOppdaterAlleNyeKrav(kravLinjer: List<KravLinje>) {
        databaseService.saveAllNewKrav(kravLinjer)

        kravLinjer.filter { !it.isNyttKrav() }.map {
            val skeKravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
            var skeKravidenLagres = skeKravident
            if (skeKravident.isBlank()) {
                val httpResponse = skeClient.getSkeKravident(it.referanseNummerGammelSak)
                if (httpResponse.status.isSuccess()){
                    skeKravidenLagres = httpResponse.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                }
            }
            if (skeKravidenLagres.isNotBlank()) databaseService.updateSkeKravidintifikator(it.saksNummer, skeKravidenLagres)
        }
    }
}
