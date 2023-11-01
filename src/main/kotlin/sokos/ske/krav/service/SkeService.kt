package sokos.ske.krav.service

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics.antallKravLest
import sokos.ske.krav.metrics.Metrics.antallKravSendt
import sokos.ske.krav.util.erEndring
import sokos.ske.krav.util.erNyttKrav
import sokos.ske.krav.util.erStopp
import sokos.ske.krav.util.fileValidator
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
	private val kravService: KravService = KravService(PostgresDataSource()),
	private val ftpService: FtpService = FtpService()
) {
	private val logger = KotlinLogging.logger {}

    fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles { fileValidator(it) }

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        logger.info { "Starter skeService SendNyeFtpFilertilSkatt." }
        val files = ftpService.getValidatedFiles { fileValidator(it) }
        logger.info { "Antall filer i kjøring ${files.size}" }

		val fnrListe = getFnrListe()
		var fnrIter = fnrListe.listIterator()

        val responses = files.map { file ->
			logger.info { "Antall linjer i ${file.name}: ${file.kravLinjer.size} (incl. start/stop)" }
            val svar: List<Pair<KravLinje, HttpResponse>> = file.kravLinjer.map {
				antallKravLest.increment()

				val substfnr = if (fnrIter.hasNext()) fnrIter.next()
								else {
									fnrIter = fnrListe.listIterator(0)
									fnrIter.next()
								}

				var kravident = kravService.hentSkeKravident(it.saksNummer)
				val request: String
				if (kravident.isEmpty() && !it.erNyttKrav()) {

					logger.error {
						"""
						SAKSNUMMER: ${it.saksNummer}
						Hva F* gjør vi nå, dette skulle ikke skje
						linjenr: ${it.linjeNummer}: ${file.content[it.linjeNummer]}
						"""
					//hva faen gjør vi nå??
					//Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
					}
				}

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
										saksNummer = kravService.lagreNyKobling(it.saksNummer),
										gjelderID = substfnr
									)
								)
							)
						skeClient.opprettKrav(request)
					}

					else -> throw Exception("SkeService: Feil linjetype")
				}

				if (response.status.isSuccess() || response.status.value in (409 until 422)) {
					antallKravSendt.increment()
					if (it.erNyttKrav())
						kravident =
							Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText()).kravidentifikator

					kravService.lagreNyttKrav(
						kravident,
						request,
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

			/*val (httpResponseOk, httpResponseFailed) = svar.partition { it.second.status.isSuccess()  }
			val failedLines = httpResponseFailed.map { FailedLine(file, parseDetailLinetoFRData(it.first), it.second.status.value.toString(), it.second.bodyAsText()) }
			handleAnyFailedLines(failedLines)*/

            svar
        }

        files.forEach { file ->  ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        return responses.map { it.map { it.second } }.flatten()
    }


	suspend fun hentOgOppdaterMottaksStatus(): List<String> {
		val antall = AtomicInteger()
		val feil = AtomicInteger()
		val result = kravService.hentAlleKravSomIkkeErReskotrofort().map {
			antall.incrementAndGet()

			val response = skeClient.hentMottaksStatus(it.saksnummerSKE)
			if (response.status.isSuccess()) {
				try {
					val body = response.bodyAsText()
					val mottaksstatus = Json.decodeFromString<MottaksStatusResponse>(body)

					kravService.oppdaterStatus(mottaksstatus)
				} catch (e: Exception) {
					feil.incrementAndGet()
					logger.error { "Logger Exception: ${e.message}" }
					throw e
				}
			}

			"Status ok: ${response.status.value}, ${response.bodyAsText()}"
		}

		return result + "Antall behandlet  $antall, Antall feilet: $feil"
	}

	suspend fun hentValideringsfeil(): List<String> {
		val resultat = kravService.hentAlleKravMedValideringsfeil().map {
			val response = skeClient.hentValideringsfeil(it.saksnummerSKE)

			if (response.status.isSuccess()) {
				val valideringsfeilResponse = Json.decodeFromString<ValideringsFeilResponse>(response.bodyAsText())
				kravService.lagreValideringsfeil(valideringsfeilResponse, it.saksnummerSKE)
				"Status OK: ${response.bodyAsText()}"
			} else {
				"Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
			}
		}
		if (resultat.isNotEmpty()) logger.info { "HENTVALIDERINGSFEIL: Det er hentet valideringsfeil for ${resultat.size} krav" }

		return resultat
	}

}

