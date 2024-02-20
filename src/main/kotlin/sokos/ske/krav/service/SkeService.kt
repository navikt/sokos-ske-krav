package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.*
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.set


const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_RENTER = "ENDRE_RENTER"
const val ENDRE_HOVEDSTOL = "ENDRE_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"


class SkeService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService(),
    private val ftpService: FtpService = FtpService(),
) {
    private val logger = KotlinLogging.logger {}

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles()

    suspend fun handleNewKrav() {
        sendNewFilesToSKE()
        hentOgOppdaterMottaksStatus()
    }

    suspend fun sendNewFilesToSKE(): List<HttpResponse> {
        val files = ftpService.getValidatedFiles()
        logger.info("*******************${LocalDateTime.now()}*******************")
        logger.info("Starter innsending av ${files.size} filer")
        val fnrListe = getFnrListe()
        val fnrIter = fnrListe.listIterator()

        val responses = files.map { file ->
            logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size - 2}")
            sendKrav(file, fnrIter, fnrListe)
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        logger.info("*******************KJØRING FERDIG*******************")
        return responses.flatten()
    }

    data class RequestResult(
        val response: HttpResponse,
        val krav: KravLinje,
        val request: String,
        val kravIdentifikator: String,
        val corrId: String
    )

    private suspend fun sendKrav(
        file: FtpFil,
        fnrIter: ListIterator<String>,
        fnrListe: List<String>
    ): List<HttpResponse> {
        var fnrIter1 = fnrIter

        val linjer = file.kravLinjer.filter { LineValidator.validateLine(it, file.name) }

        databaseService.saveAllNewKrav(linjer)

        val allResponses = mutableListOf<RequestResult>()


        linjer.forEach {
            Metrics.numberOfKravRead.inc()
            val responsesMap = mutableMapOf<String, RequestResult>()

            val substfnr = if (fnrIter1.hasNext()) {
                fnrIter1.next()
            } else {
                fnrIter1 = fnrListe.listIterator(0)
                fnrIter1.next()
            }

            var kravIdentifikator = databaseService.getSkeKravident(it.referanseNummerGammelSak)
            var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR

            if (kravIdentifikator.isEmpty() && !it.isNyttKrav()) {
                kravIdentifikator = it.referanseNummerGammelSak
                kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
            }
            val corrID = UUID.randomUUID().toString()
            when {
                it.isStopp() -> {
                    val response = sendStoppKrav(kravIdentifikator, kravIdentifikatorType, it, corrID)
                    responsesMap[STOPP_KRAV] = response
                }

                it.isEndring() -> {
                    val endreResponses = sendEndreKrav(kravIdentifikator, kravIdentifikatorType, it, corrID)
                    responsesMap.putAll(endreResponses)
                }

                it.isNyttKrav() -> {
                    val response = sendOpprettKrav(it, substfnr, corrID)
                    if (response.response.status.isSuccess()) kravIdentifikator =
                        response.response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator

                    responsesMap[NYTT_KRAV] = response
                }
            }

            databaseService.updateSentKravToDatabase(responsesMap, it, kravIdentifikator)

            allResponses.addAll(responsesMap.values)
        }

        allResponses.filter { !it.response.status.isSuccess() }
            .forEach(){
                databaseService.saveErrorMessageToDatabase(it.request, it.response, it.krav, it.kravIdentifikator, it.corrId)
            }

        return emptyList()  //TODO TJA HVA DA
    }


    private suspend fun sendStoppKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravLinje,
        corrID: String
    ): RequestResult {
        val request = makeStoppKravRequest(kravIdentifikator, kravIdentifikatorType)
        val response = skeClient.stoppKrav(request, corrID)

        val requestResult = RequestResult(
            response = response,
            request = Json.encodeToString(request),
            krav = krav,
            kravIdentifikator = kravIdentifikator,
            corrId = corrID
        )

        return requestResult
    }

    private suspend fun sendEndreKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravLinje,
        corrID: String
    ): Map<String, RequestResult> {

        skeClient.endreOppdragsGiversReferanse(makeNyOppdragsgiversReferanseRequest(krav), kravIdentifikator, kravIdentifikatorType)

        val endreRenterRequest = makeEndreRenteRequest(krav)
        val endreRenterResponse = skeClient.endreRenter(endreRenterRequest, kravIdentifikator, kravIdentifikatorType, corrID)

        val requestResultEndreRente = RequestResult(
            response = endreRenterResponse,
            request = Json.encodeToString(endreRenterRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = corrID
        )

        val corrIdHovedStol = UUID.randomUUID().toString()
        val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
        val endreHovedstolResponse =
            skeClient.endreHovedstol(endreHovedstolRequest, kravIdentifikator, kravIdentifikatorType, corrIdHovedStol)

        val requestResultEndreHovedstol = RequestResult(
            response = endreHovedstolResponse,
            request = Json.encodeToString(endreHovedstolRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = corrIdHovedStol
        )

        val responseMap = mapOf(ENDRE_RENTER to requestResultEndreRente, ENDRE_HOVEDSTOL to requestResultEndreHovedstol)
        return responseMap
    }

    private suspend fun sendOpprettKrav(krav: KravLinje, substfnr: String, corrID: String): RequestResult {
        val opprettKravRequest = makeOpprettKravRequest(
            krav.copy(
                gjelderID =
                if (krav.gjelderID.startsWith("00")) krav.gjelderID else substfnr
            ), databaseService.insertNewKobling(krav.saksNummer, corrID)
        )
        val response = skeClient.opprettKrav(opprettKravRequest, corrID)

        val requestResult = RequestResult(
            response = response,
            request = Json.encodeToString(opprettKravRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = corrID
        )

        return requestResult
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
        )
    }

    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        var antall = 0
        var feil = 0

        val start = Clock.System.now()
        val krav = databaseService.hentAlleKravSomIkkeErReskotrofort()

        var tidSiste = Clock.System.now()
        var tidHentAlleKrav = (tidSiste-start).inWholeMilliseconds
        var tidHentMottakstatus = 0L
        var tidOppdaterstatus = 0L
        val result = krav.map {

            var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
            var kravIdentifikator = it.saksnummerSKE

            if (it.saksnummerSKE.isEmpty()) {
                kravIdentifikator = it.referanseNummerGammelSak
                kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
            }
            antall++
            val response = skeClient.getMottaksStatus(kravIdentifikator, kravIdentifikatorType)

            tidHentMottakstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
            tidSiste = Clock.System.now()

            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus)
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
}
