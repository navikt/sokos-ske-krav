package sokos.ske.krav.service

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.api.model.requests.AvskrivingRequest
import sokos.ske.krav.api.model.requests.EndringRequest
import sokos.ske.krav.api.model.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.api.model.responses.MottaksStatusResponse
import sokos.ske.krav.api.model.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.api.model.responses.ValideringsFeilResponse
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.DetailLine
import sokos.ske.krav.util.fileValidator
import sokos.ske.krav.util.lagEndreKravRequest
import sokos.ske.krav.util.lagOpprettKravRequest
import sokos.ske.krav.util.lagStoppKravRequest
import sokos.ske.krav.util.parseDetailLinetoFRData
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

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
	private inline fun <reified T> toJson(serializer: SerializationStrategy<T>, body: T) =
		builder.encodeToJsonElement(serializer, body).toString()

	@OptIn(ExperimentalSerializationApi::class)
	private val builder = Json {
		encodeDefaults = true
		explicitNulls = false
	}

	fun testListFiles(directory: String): List<String> = ftpService.listAllFiles(directory)
	fun testFtp(): List<FtpFil> = ftpService.getValidatedFiles(::fileValidator)

	suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
		logger.info { "Starter skeService SendNyeFtpFilertilSkatt." }
		val files = ftpService.getValidatedFiles(::fileValidator)
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
						request = toJson(AvskrivingRequest.serializer(), lagStoppKravRequest(kravident))
						skeClient.stoppKrav(request)
					}

					it.erEndring() -> {
						request = toJson(EndringRequest.serializer(), lagEndreKravRequest(it, kravident))
						skeClient.endreKrav(request)
					}

					it.erNyttKrav() -> {
						request = toJson(
							OpprettInnkrevingsoppdragRequest.serializer(),
							lagOpprettKravRequest(it.copy(saksNummer = kravService.lagreNyKobling(it.saksNummer)))
						)
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
						parseDetailLinetoFRData(it),
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

		files.forEach { file -> ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

		return responses.map { response -> response.map { it.second } }.flatten()
	}


	suspend fun hentOgOppdaterMottaksStatus(): List<String> {
		val antall = AtomicInteger()
		val feil = AtomicInteger()
		val result = kravService.hentAlleKravSomIkkeErReskotrofort().map {
			antall.incrementAndGet()
			logger.info { "Logger (Status start): ${it.saksnummerSKE}" }

			val response = skeClient.hentMottaksStatus(it.saksnummerSKE)
			logger.info { "Logger (Status hentet): ${it.saksnummerSKE}" }
			if (response.status.isSuccess()) {
				logger.info { "Logger (Status success): ${it.saksnummerSKE}" }
				try {
					val body = response.bodyAsText()
					logger.info { "Logger status body: $body" }
					val mottaksstatus = Json.decodeFromString<MottaksStatusResponse>(body)
					logger.info { "Logger mottaksresponse: $mottaksstatus, Body: $body" }
					kravService.oppdaterStatus(mottaksstatus)
					logger.info { "Logger (Status oppdatert): ${it.saksnummerSKE}" }
				} catch (e: Exception) {
					feil.incrementAndGet()
					logger.error { "Logger Exception: ${e.message}" }
					throw e
				}
			}
			logger.info { "Logger (Status ferdig): ${it.saksnummerSKE}" }
			"Status ok: ${response.status.value}, ${response.bodyAsText()}"
		}
		logger.info { "Loger status: ferdig  (antall $antall, feilet: $feil) commit og closer connectin" }
		return result + "Antall behandlet  $antall, Antall feilet: $feil"
	}

	suspend fun hentValideringsfeil(): List<String> {
		val resultat = kravService.hentAlleKravMedValideringsfeil().map {
			val response = skeClient.hentValideringsfeil(it.saksnummerSKE)

			if (response.status.isSuccess()) {
				logger.info { "Logger (validering success): ${it.saksnummerSKE}" }

				val valideringsfeilResponse = Json.decodeFromString<ValideringsFeilResponse>(response.bodyAsText())
				kravService.lagreValideringsfeil(valideringsfeilResponse, it.saksnummerSKE)
				//lag ftpfil og  kall handleAnyFailedFiles
				"Status OK: ${response.bodyAsText()}"
			} else {
				logger.info { "Logger (Fikk ikke hentet valideringsfeil for:  ${it.saksnummerSKE}, Status: ${response.status.value})" }
				"Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
			}
		}
		if (resultat.isNotEmpty()) logger.info { "HENTVALIDERINGSFEIL: Det er ${resultat.size} krav det er hentet valideringsfeil for" }

		return resultat
	}

}

private fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
private fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
private fun DetailLine.erStopp() = (belop.roundToLong() == 0L)
