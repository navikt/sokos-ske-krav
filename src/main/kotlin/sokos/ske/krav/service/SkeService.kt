package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.FEIL_MED_ENDRING
import sokos.ske.krav.database.KONFLIKT_409
import sokos.ske.krav.database.KRAV_SENDT
import sokos.ske.krav.database.VALIDERINGSFEIL_422
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isNyttKrav
import sokos.ske.krav.util.isStopp
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.makeNyHovedStolRequest
import sokos.ske.krav.util.makeEndreRenteRequest
import sokos.ske.krav.util.makeOpprettKravRequest
import sokos.ske.krav.util.makeStoppKravRequest
import java.util.concurrent.atomic.AtomicInteger


const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_KRAV = "ENDRE_KRAV"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService(),
    private val ftpService: FtpService = FtpService(),
) {
    private val logger = KotlinLogging.logger{}

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles()

    suspend fun sendNewFilesToSKE(): List<HttpResponse> {
        logger.info("Starter skeService SendNyeFtpFilertilSkatt.")
        val files = ftpService.getValidatedFiles()
        logger.info("Antall filer i kjøring ${files.size}")

        val fnrListe = getFnrListe()
        val fnrIter = fnrListe.listIterator()

        val responses = files.map { file ->
            logger.info("Antall linjer i ${file.name}: ${file.kravLinjer.size} (incl. start/stop)")
            sendKrav(file, fnrIter, fnrListe)
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        return responses.flatten()
    }

    private suspend fun sendStoppKrav(kravident: String, kravidentType: Kravidentifikatortype, linje: KravLinje, file: FtpFil): HttpResponse{
        val stoppresponse = skeClient.stoppKrav(makeStoppKravRequest(kravident, kravidentType))

        if(!stoppresponse.status.isSuccess()) {
            // FILEN MÅ FØLGES OPP MANUELT...

            logger.info("FEIL FRA SKATT! FANT IKKE ORIGINALSAKSREF! MÅ GJØRE NOE")
            handleHvaFGjorViNaa(linje, file)
        }
        return stoppresponse
    }

    private suspend fun sendEndreKrav(kravident: String, kravidentType: Kravidentifikatortype, linje: KravLinje): List<HttpResponse>{

        //Skatt bruker ikke nytt saksref til noe så da sender vi det ikke (men venter på endelig avklaring fra skatt)
        //skeClient.endreOppdragsGiversReferanse(lagNyOppdragsgiversReferanseRequest(linje), kravident, kravidentType)

        val renteresponse = skeClient.endreRenter(makeEndreRenteRequest(linje), kravident, kravidentType)
        val hovedstolResponse = skeClient.endreHovedstol(makeNyHovedStolRequest(linje), kravident, kravidentType)

        return listOf(renteresponse, hovedstolResponse)

    }
    private suspend fun sendOpprettKrav(linje: KravLinje, substfnr: String): HttpResponse{
      val response =  skeClient.opprettKrav(
            makeOpprettKravRequest(
                linje.copy(
                    gjelderID = if(linje.gjelderID.startsWith("00")) linje.gjelderID else substfnr,
                ),
                databaseService.insertNewKobling(linje.saksNummer)
            ),
        )
        return response
    }


    private suspend fun sendKrav(file: FtpFil, fnrIter: ListIterator<String>, fnrListe: List<String>) : List<HttpResponse>{
        var fnrIter1 = fnrIter

        //Klønete pga endring av krav
        val allResponses = mutableListOf<HttpResponse>()

        val linjer = file.kravLinjer.filter { LineValidator.validateLine(it, file.name) }
        logger.info("${linjer.size} LINJER")

        //bruker foreach for å ha litt bedre oversikt, for tror det må endres siden endring av krav gjør det så teit
        linjer.forEach {
            Metrics.numberOfKravRead.inc()

            val responses = mutableListOf<HttpResponse>()

            val substfnr = if (fnrIter1.hasNext()) {
                fnrIter1.next()
            } else {
                fnrIter1 = fnrListe.listIterator(0)
                fnrIter1.next()
            }


            var kravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
            var kravidentType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR

            if (kravident.isEmpty() && !it.isNyttKrav()) {
                kravident = it.referanseNummerGammelSak
                kravidentType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
            }

            when {
                it.isStopp() -> {
                    val response = sendStoppKrav(kravident, kravidentType, it, file)
                    if(!response.status.isSuccess()) {
                        logger.info("FEIL i innsending av STOPP PÅ LINJE ${it.linjeNummer}: ${response.status} ${response.bodyAsText()}")
                        logger.info("$it")
                    }
                    responses.add(response)
                }
                it.isEndring() -> {
                    val endreResponses = sendEndreKrav(kravident, kravidentType,it)
                    endreResponses.filter { resp -> !resp.status.isSuccess() }.forEach{ resp ->
                        logger.info("FEIL I INNSENDING AV ENDRING  PÅ LINJE ${it.linjeNummer}: ${resp.status} ${resp.bodyAsText()}")
                        logger.info("$it")
                    }
                    responses.addAll(endreResponses)
                }
                it.isNyttKrav() -> {
                    val response = sendOpprettKrav(it, substfnr)

                    if(!response.status.isSuccess()) {
                        logger.info("FEIL i innsending av NYTT PÅ LINJE ${it.linjeNummer}: ${response.status}  ${response.bodyAsText()}")
                        logger.info("$it")
                    } else  kravident = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                    responses.add(response)
                }
            }

            saveSentKravToDatabase(responses, it, kravident)

            allResponses.addAll(responses)
            responses.clear()
        }

        val errors = allResponses.filter { resp ->
            !resp.status.isSuccess()  &&  resp.status.value !in (HttpStatusCode.Conflict.value until HttpStatusCode.UnprocessableEntity.value)
        }
        if(errors.isNotEmpty()){
            //ALARM
        }
        return allResponses

    }

    private suspend fun saveSentKravToDatabase(allResponses: List<HttpResponse>, krav: KravLinje, kravident: String){
        var kravidentToBeSaved = kravident




        allResponses.forEach {
            response ->

            Metrics.numberOfKravSent.inc()
            Metrics.typeKravSent.labels(krav.stonadsKode).inc()

            val statusString =
                if(allResponses.filter { resp -> resp.status.isSuccess() }.size == allResponses.size) KRAV_SENDT
                else if(response.status.isSuccess()) FEIL_MED_ENDRING
                else if(response.status.value == HttpStatusCode.Conflict.value) KONFLIKT_409
                else if(response.status.value == HttpStatusCode.UnprocessableEntity.value) VALIDERINGSFEIL_422
                else if(response.status.value == HttpStatusCode.NotFound.value) "SKE HAR IKKE REFNR"
                else "UKJENT STATUS: ${allResponses.map { resp -> resp.status.value }}"


            if (krav.isNyttKrav()) {
                kravidentToBeSaved = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
            }else{
               if(kravidentToBeSaved==krav.referanseNummerGammelSak){
                   kravidentToBeSaved=""
               }
            }

            databaseService.insertNewKrav(
                kravidentToBeSaved,
                krav,
                when {
                    krav.isStopp() -> STOPP_KRAV
                    krav.isEndring() -> ENDRE_KRAV
                    else -> NYTT_KRAV
                },
                statusString
            )
        }

    }



    private fun handleHvaFGjorViNaa(krav: KravLinje, file: FtpFil) {
        logger.error(
            """
                        SAKSNUMMER: ${krav.saksNummer}
                        GAMMELT SAKSNUMMER: ${krav.referanseNummerGammelSak}
                        Hva F* gjør vi nå, dette skulle ikke skje
                        linjenr: ${krav.linjeNummer}: ${file.content[krav.linjeNummer]}
                    """
            // hva faen gjør vi nå??
            // Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
        )
    }

    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        val antall = AtomicInteger()
        val feil = AtomicInteger()
        val result = databaseService.getAlleKravSomIkkeErReskotrofort().map {
            antall.incrementAndGet()

            val response = skeClient.getMottaksStatus(it.saksnummerSKE)
            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus)
                } catch (e: SerializationException) {
                    feil.incrementAndGet()
                    logger.error("Feil i dekoding av MottaksStatusResponse: ${e.message}")
                    throw e
                } catch (e: IllegalArgumentException) {
                    feil.incrementAndGet()
                    logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}" )
                    throw e
                }
            }

            "Status ok: ${response.status.value}, ${response.bodyAsText()}"
        }

        return result + "Antall behandlet  $antall, Antall feilet: $feil"
    }

    suspend fun hentValideringsfeil(): List<String> {
        val resultat = databaseService.getAlleKravMedValideringsfeil().map {
            val response = skeClient.getValideringsfeil(it.saksnummerSKE)

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
