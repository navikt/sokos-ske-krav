package sokos.ske.krav.service

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.nav.DetailLine
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.util.*
import java.util.concurrent.atomic.AtomicInteger

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_KRAV = "ENDRE_KRAV"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
	private val skeClient: SkeClient,
	dataSource: PostgresDataSource = PostgresDataSource(),
	private val ftpService: FtpService = FtpService()
) {
	private val logger = KotlinLogging.logger {}
	private val kravService = KravService(dataSource)


    suspend fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
    suspend fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles { fileValidator(it) }

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        logger.info { "Starter skeService SendNyeFtpFilertilSkatt." }
        val files = ftpService.getValidatedFiles { fileValidator(it) }
        logger.info { "Antall filer i kjøring ${files.size}" }

        val responses = files.map { file ->
            val svar: List<Pair<DetailLine, HttpResponse>> = file.detailLines.map {

				var kravident = kravService.hentSkeKravident(it.saksNummer)
				val request: String
				if (kravident.isEmpty() && !it.erNyttKrav()) {

					println("SAKSNUMMER: ${it.saksNummer}")
					println("Hva faen gjør vi nå :( ")
					//hva faen gjør vi nå??
					//Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
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
							Json.encodeToString(lagOpprettKravRequest(it.copy(saksNummer = kravService.lagreNyKobling(it.saksNummer))))
						skeClient.opprettKrav(request)
					}

					else -> throw Exception("SkeService: Feil linjetype")
				}

				if (response.status.isSuccess() || response.status.value in (409 until 422)) {
					if (it.erNyttKrav())
						kravident =
							Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText()).kravidentifikator

					kravService.lagreNyttKrav(
						kravident,
						request,
						it.originalLinje,
						it,
						when {
							it.erStopp() -> STOPP_KRAV
							it.erEndring() -> ENDRE_KRAV
							else -> NYTT_KRAV
						},
						response
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
				//lag ftpfil og  kall handleAnyFailedFiles
				"Status OK: ${response.bodyAsText()}"
			} else {
				"Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
			}
		}
		if (resultat.isNotEmpty()) logger.info { "HENTVALIDERINGSFEIL: Det er ${resultat.size} krav det er hentet valideringsfeil for" }

		return resultat
	}

}

