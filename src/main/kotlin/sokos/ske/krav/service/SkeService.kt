package sokos.ske.krav.service

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics.antallKravLest
import sokos.ske.krav.metrics.Metrics.antallKravSendt
import sokos.ske.krav.util.erEndring
import sokos.ske.krav.util.erNyttKrav
import sokos.ske.krav.util.erStopp
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.lagEndreKravRequest
import sokos.ske.krav.util.lagOpprettKravRequest
import sokos.ske.krav.util.lagStoppKravRequest
import java.util.concurrent.atomic.AtomicInteger

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_KRAV = "ENDRE_KRAV"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService(),
    private val ftpService: FtpService = FtpService()
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

        return responses.map { it.map { it.second } }.flatten()
    }

    private suspend fun sendAlleLinjer(
        file: FtpFil,
        fnrIter: ListIterator<String>,
        fnrListe: List<String>
    ): List<Pair<KravLinje, HttpResponse>> {
        var fnrIter1 = fnrIter
        return file.kravLinjer.map {
            antallKravLest.increment()

            val substfnr = if (fnrIter1.hasNext()) fnrIter1.next()
            else {
                fnrIter1 = fnrListe.listIterator(0)
                fnrIter1.next()
            }

            var kravident = databaseService.hentSkeKravident(it.saksNummer)
            val request: String

            if (kravident.isEmpty() && !it.erNyttKrav()) handleHvaFGjorViNaa(it, file)

            val response = when {
                it.erStopp() -> {
                    request = Json.encodeToString(lagStoppKravRequest(kravident))
                    skeClient.stoppKrav(request)
                }

                it.erEndring() -> {
                    request = Json.encodeToString(lagEndreKravRequest(it, kravident))
                    skeClient.endreKrav(request)
                }

                it.erNyttKrav() -> {
                    request =
                        Json.encodeToString(
                            lagOpprettKravRequest(
                                it.copy(
                                    saksNummer = databaseService.lagreNyKobling(it.saksNummer),
                                    gjelderID = substfnr
                                )
                            )
                        )
                    skeClient.opprettKrav(request)
                }

                else -> throw IllegalArgumentException("SkeService: Feil linjetype/linjetype kan ikke identifiseres")
            }

            if (response.status.isSuccess() || response.status.value in (409 until 422)) {
                antallKravSendt.increment()
                if (it.erNyttKrav())
                    kravident =
                        Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText()).kravidentifikator

                databaseService.lagreNyttKrav(
                    kravident,
                    it,
                    when {
                        it.erStopp() -> STOPP_KRAV
                        it.erEndring() -> ENDRE_KRAV
                        else -> NYTT_KRAV
                    },
                    response.status
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
                        Hva F* gjør vi nå, dette skulle ikke skje
                        linjenr: ${krav.linjeNummer}: ${file.content[krav.linjeNummer]}
                    """
            //hva faen gjør vi nå??
            //Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
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
                    val body = response.bodyAsText()
                    val mottaksstatus = Json.decodeFromString<MottaksStatusResponse>(body)

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
                val valideringsfeilResponse = Json.decodeFromString<ValideringsFeilResponse>(response.bodyAsText())
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

