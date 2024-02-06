package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.KONFLIKT_409
import sokos.ske.krav.database.KRAV_SENDT
import sokos.ske.krav.database.VALIDERINGSFEIL_422
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics.antallKravLest
import sokos.ske.krav.metrics.Metrics.antallKravSendt
import sokos.ske.krav.metrics.Metrics.typeKravSendt
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.erEndring
import sokos.ske.krav.util.erNyttKrav
import sokos.ske.krav.util.erStopp
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.lagNyHovedStolRequest
import sokos.ske.krav.util.lagEndreRenteRequest
import sokos.ske.krav.util.lagOpprettKravRequest
import sokos.ske.krav.util.lagStoppKravRequest
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

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
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

    private suspend fun sendStoppKrav(kravident: String, kravidentType: Kravidentifikatortype, linje: KravLinje, file: FtpFil): List<HttpResponse>{
        val stoppresponse = skeClient.stoppKrav(lagStoppKravRequest(kravident, kravidentType))

        if(!stoppresponse.status.isSuccess()) {
            // FILEN MÅ FØLGES OPP MANUELT...
            // LAGRES PÅ FILOMRÅDE...

            println("FEIL FRA SKATT! FANT IKKE ORIGINALSAKSREF! MÅ LAGRE PÅ FILOMRÅDE")
            handleHvaFGjorViNaa(linje, file)
        }
        return listOf(stoppresponse)
    }

    private suspend fun sendEndreKrav(kravident: String, kravidentType: Kravidentifikatortype, linje: KravLinje): List<HttpResponse>{

        //Skatt bruker ikke nytt saksref til noe så da sender vi det ikke (men venter på endelig avklaring fra skatt)
        //skeClient.endreOppdragsGiversReferanse(lagNyOppdragsgiversReferanseRequest(linje), kravident, kravidentType)

        val renteresponse = skeClient.endreRenter(lagEndreRenteRequest(linje), kravident, kravidentType)
        val hovedstolResponse = skeClient.endreHovedstol(lagNyHovedStolRequest(linje), kravident, kravidentType)

        //her må vi lagre transaksjonsIDene

        return listOf(renteresponse, hovedstolResponse, renteresponse)

    }
    private suspend fun sendNyeKrav(linje: KravLinje, substfnr: String): List<HttpResponse>{
      val response =  skeClient.opprettKrav(
            lagOpprettKravRequest(
                linje.copy(
                    gjelderID = if(linje.gjelderID.startsWith("00")) linje.gjelderID else substfnr,
                ),
                databaseService.lagreNyKobling(linje.saksNummer)
            ),
        )
        return listOf(response)
    }


    private suspend fun sendKrav(file: FtpFil, fnrIter: ListIterator<String>, fnrListe: List<String>) : List<HttpResponse>{
        var fnrIter1 = fnrIter

        //Klønete pga endring av krav
        val allResponses = mutableListOf<HttpResponse>()

        val linjer = file.kravLinjer.filter { LineValidator.validateLine(it, file.name) }
        println("${linjer.size} LINJER")

        //bruker foreach for å ha litt bedre oversikt, for tror det må endres siden endring av krav gjør det så teit
        linjer.forEach {
            antallKravLest.increment()


            val substfnr = if (fnrIter1.hasNext()) {
                fnrIter1.next()
            } else {
                fnrIter1 = fnrListe.listIterator(0)
                fnrIter1.next()
            }

            var kravident = databaseService.hentSkeKravident(it.referanseNummerGammelSak)
            var kravidentType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR

            if (kravident.isEmpty() && !it.erNyttKrav()) {

                try {
                    val response = skeClient.hentSkeKravident(it.referanseNummerGammelSak)

                    println("RESPONSE FRA KALL: ${response.bodyAsText()}")
                    kravident = response.body<AvstemmingResponse>().kravidentifikator
                    println("FANT KRAVIDENT FRA AVSTEMMING: $kravident")
                }    catch (e: Exception){
                    println("FEIL I KALL!")
                }
                if(kravident.isEmpty()){
                    kravident = it.referanseNummerGammelSak
                    kravidentType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
                }
            }

            when {
                it.erStopp() -> {
                    val response = sendStoppKrav(kravident, kravidentType, it, file).first()
                    if(!response.status.isSuccess()) {
                        println("FEIL i innsending av STOPP PÅ LINJE ${it.linjeNummer}: ${response.status} ${response.bodyAsText()}")
                        println(it)
                    }
                    allResponses.add(response)
                }
                it.erEndring() -> {
                    val responses = sendEndreKrav(kravident, kravidentType,it)
                    responses.filter { resp -> !resp.status.isSuccess() }.forEach{ resp ->
                        println("FEIL I INNSENDING AV ENDRING  PÅ LINJE ${it.linjeNummer}: ${resp.status} ${resp.bodyAsText()}")
                        println(it)
                    }
                    allResponses.addAll(responses)
                }
                it.erNyttKrav() -> {
                    val response = sendNyeKrav(it, substfnr).first()

                    if(!response.status.isSuccess()) {
                        println("FEIL i innsending av NYTT PÅ LINJE ${it.linjeNummer}: ${response.status}  ${response.bodyAsText()}")
                        println(it)
                    } else  kravident = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                    allResponses.add(response)
                }
            }

            lagreSendtKravIDatabase(allResponses, it, kravident)

        }
        return allResponses

    }

    private suspend fun lagreSendtKravIDatabase(allResponses: List<HttpResponse>, krav: KravLinje, kravident: String){
        var kravidentSomSkalLagres = kravident

        //Endring av krav gjør dette veldig klønete siden vi da har 3 responses. Må finne en bedre løsning
        val errors = allResponses.filter { resp ->
            !resp.status.isSuccess()  &&  resp.status.value !in (HttpStatusCode.Conflict.value until HttpStatusCode.UnprocessableEntity.value)
        }
        if(errors.isEmpty()){
            antallKravSendt.increment()
            typeKravSendt(krav.stonadsKode).increment()
            if (krav.erNyttKrav()) {
                kravidentSomSkalLagres = allResponses[0].body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
            }

            //dette er også pga endring av krav og er ikke noe bra for det bruker any
            val statusString =
                if(allResponses.filter { resp -> resp.status.isSuccess() }.size == allResponses.size) KRAV_SENDT
                else if (allResponses.any { resp -> resp.status.value == HttpStatusCode.Conflict.value }) KONFLIKT_409
                else if(allResponses.any { resp -> resp.status.value == HttpStatusCode.UnprocessableEntity.value }) VALIDERINGSFEIL_422
                else "UKJENT STATUS: ${allResponses.map { resp -> resp.status.value }}"

            databaseService.lagreNyttKrav(
                kravidentSomSkalLagres,
                krav,
                when {
                    krav.erStopp() -> STOPP_KRAV
                    krav.erEndring() -> ENDRE_KRAV
                    else -> NYTT_KRAV
                },
                statusString
            )
        } else{
            // FEIL I LINJE, må lagres i feilfil


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
        val result = databaseService.hentAlleKravSomIkkeErReskotrofort().map {
            antall.incrementAndGet()

            val response = skeClient.hentMottaksStatus(it.saksnummerSKE)
            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.oppdaterStatus(mottaksstatus)
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
        val resultat = databaseService.hentAlleKravMedValideringsfeil().map {
            val response = skeClient.hentValideringsfeil(it.saksnummerSKE)

            if (response.status.isSuccess()) {
                val valideringsfeilResponse = response.body<ValideringsFeilResponse>()
                databaseService.lagreValideringsfeil(valideringsfeilResponse, it.saksnummerSKE)
                "Status OK: ${response.bodyAsText()}"
            } else {
                "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
            }
        }
        if (resultat.isNotEmpty()) logger.info("HENTVALIDERINGSFEIL: Det er hentet valideringsfeil for ${resultat.size} krav")

        return resultat
    }
}
