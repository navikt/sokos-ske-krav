package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics.antallKravLest
import sokos.ske.krav.metrics.Metrics.antallKravSendt
import sokos.ske.krav.metrics.Metrics.typeKravSendt
import sokos.ske.krav.util.erEndring
import sokos.ske.krav.util.erNyttKrav
import sokos.ske.krav.util.erStopp
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.lagNyHovedStolRequest
import sokos.ske.krav.util.lagEndreRenteRequest
import sokos.ske.krav.util.lagNyOppdragsgiversReferanseRequest
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
    private val logger = KotlinLogging.logger {}

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles()

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        logger.info { "Starter skeService SendNyeFtpFilertilSkatt." }
        val files = ftpService.getValidatedFiles()
        logger.info { "Antall filer i kjøring ${files.size}" }

        val fnrListe = getFnrListe()
        val fnrIter = fnrListe.listIterator()

        val responses = files.map { file ->
            logger.info { "Antall linjer i ${file.name}: ${file.kravLinjer.size} (incl. start/stop)" }

            val svar: List<Pair<KravLinje, HttpResponse>> = sendAlleLinjer(file, fnrIter, fnrListe)
            svar
        }

        files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        return responses.map { it.map { response ->  response.second } }.flatten()
    }

    private suspend fun sendAlleLinjer(
        file: FtpFil,
        fnrIter: ListIterator<String>,
        fnrListe: List<String>,
    ): List<Pair<KravLinje, HttpResponse>> {
        var fnrIter1 = fnrIter
        return file.kravLinjer.map {
            antallKravLest.increment()

            val substfnr = if (fnrIter1.hasNext()) {
                fnrIter1.next()
            } else {
                fnrIter1 = fnrListe.listIterator(0)
                fnrIter1.next()
            }

            //Her bruker vi det NYE saksnummeret til å finne kravident.... men vi må vel bruke det GAMLE?
            var kravident = databaseService.hentSkeKravident(it.saksNummer)
            var kravidentType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR


            if (kravident.isEmpty() && !it.erNyttKrav()) {

                //Endringer og stopp har gammel saksref
                kravident = databaseService.hentSkeKravident(it.referanseNummerGammelSak)
                //Hvis det er blankt så har ikke originalkravet gått gjennom vårt system. Bruker gammel ref og håper at det er originalref
                if(kravident.isEmpty()) kravident = it.referanseNummerGammelSak

                kravidentType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
            }

            val response = when {
                it.erStopp() -> {
                    println("STOPP! Kravident = $kravident, kravidentType = ${kravidentType.value}")
                    val stoppresponse = skeClient.stoppKrav(lagStoppKravRequest(kravident, kravidentType))
                    println("STOPPRESPONSE: ${stoppresponse.bodyAsText()}")
                    if(!stoppresponse.status.isSuccess()) {
                        // FILEN MÅ FØLGES OPP MANUELT...
                        // LAGRES PÅ FILOMRÅDE...

                        println("FEIL FRA SKATT! FANT IKKE ORIGINALSAKSREF! MÅ LAGRE PÅ FILOMRÅDE")
                        handleHvaFGjorViNaa(it, file)
                    }
                        stoppresponse
                }

                it.erEndring() -> {
                    val referanseResponse = skeClient.endreOppdragsGiversReferanse(lagNyOppdragsgiversReferanseRequest(it), kravident, kravidentType)
                    println("REFERANSERESPONSE: ${referanseResponse.bodyAsText()}")

                    if(!referanseResponse.status.isSuccess())  {
                        // FILEN MÅ FØLGES OPP MANUELT...
                        // LAGRES PÅ FILOMRÅDE...

                        println("FEIL FRA SKATT! FANT IKKE ORIGINALSAKSREF! MÅ LAGRE PÅ FILOMRÅDE")
                        handleHvaFGjorViNaa(it, file)
                    }
                     //dersom referanseresponse ikke er success kan vi returne tidlig og ikke gjøre dette
                    val renteresponse = skeClient.endreRenter(lagEndreRenteRequest(it), kravident, kravidentType)
                    println("RENTERESPONSE: ${renteresponse.bodyAsText()}")

                    val hovedstolResponse = skeClient.endreHovedstol(lagNyHovedStolRequest(it), kravident, kravidentType)
                    println("HOVEDSTOLRESPONSE: ${hovedstolResponse.bodyAsText()}")

                    //lagre transaksjonsIDene
                    referanseResponse
                }

                it.erNyttKrav() -> {
                    skeClient.opprettKrav(
                        lagOpprettKravRequest(
                            it.copy(
                                gjelderID = substfnr,
                            ),
                            databaseService.lagreNyKobling(it.saksNummer)
                        ),
                    )
                }

                else -> throw IllegalArgumentException("SkeService: Feil linjetype/linjetype kan ikke identifiseres")
            }

            if (response.status.isSuccess() || response.status.value in (HttpStatusCode.Conflict.value until HttpStatusCode.UnprocessableEntity.value)) {
                antallKravSendt.increment()
                typeKravSendt(it.stonadsKode).increment()
                if (it.erNyttKrav()) {
                    kravident = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
                }

                databaseService.lagreNyttKrav(
                    kravident,
                    it,
                    when {
                        it.erStopp() -> STOPP_KRAV
                        it.erEndring() -> ENDRE_KRAV
                        else -> NYTT_KRAV
                    },
                    response.status,
                )
            } else {
                logger.error("FAILED REQUEST: ${it.saksNummer}, ERROR: ${response.bodyAsText()}")
            }
            it to response
        }
    }

    private fun handleHvaFGjorViNaa(krav: KravLinje, file: FtpFil) {
        logger.error {
            """
                        SAKSNUMMER: ${krav.saksNummer}
                        GAMMELT SAKSNUMMER: ${krav.referanseNummerGammelSak}
                        Hva F* gjør vi nå, dette skulle ikke skje
                        linjenr: ${krav.linjeNummer}: ${file.content[krav.linjeNummer]}
                    """
            // hva faen gjør vi nå??
            // Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
        }
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
                    logger.error { "Feil i dekoding av MottaksStatusResponse: ${e.message}" }
                    throw e
                } catch (e: IllegalArgumentException) {
                    feil.incrementAndGet()
                    logger.error { "Response er ikke på forventet format for MottaksStatusResponse : ${e.message}" }
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
        if (resultat.isNotEmpty()) logger.info { "HENTVALIDERINGSFEIL: Det er hentet valideringsfeil for ${resultat.size} krav" }

        return resultat
    }
}
