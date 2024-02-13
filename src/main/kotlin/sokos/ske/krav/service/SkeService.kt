package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.*
import java.time.LocalDateTime
import java.util.UUID
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

  sealed class RequestResult {
	data class Success(val response: HttpResponse, val krav: KravLinje) : RequestResult()
	data class Error(val request: String, val response: HttpResponse, val krav: KravLinje, val kravID: Long) : RequestResult()
  }
  private suspend fun sendKrav(file: FtpFil, fnrIter: ListIterator<String>, fnrListe: List<String>): List<HttpResponse> {
	var fnrIter1 = fnrIter

	val linjer = file.kravLinjer.filter { LineValidator.validateLine(it, file.name) }
	val allResponses = mutableListOf<HttpResponse>()

	linjer.forEach {
	  Metrics.numberOfKravRead.inc()
	  val responsesMap = mutableMapOf<String, HttpResponse>()

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
	  val corrID = UUID.randomUUID().toString()
	  when {
		it.isStopp() -> {
		  val response = sendStoppKrav(kravident, kravidentType, it, corrID)
		  responsesMap[STOPP_KRAV] = response
		}

		it.isEndring() -> {
		  val endreResponses = sendEndreKrav(kravident, kravidentType, it, corrID)
		  responsesMap.putAll(endreResponses)
		}

		it.isNyttKrav() -> {
		  val response = sendOpprettKrav(it, substfnr,corrID)
		  if (response.status.isSuccess()) kravident = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator

		  responsesMap[NYTT_KRAV] = response
		}
	  }

	  databaseService.saveSentKravToDatabase(responsesMap, it, kravident, corrID)

	  allResponses.addAll(responsesMap.values)

	}

	val errors = allResponses.filter { !it.status.isSuccess() }
	if (errors.isNotEmpty()) {
	  //ALARM
	}
	return allResponses

  }


  private suspend fun sendStoppKrav(kravident: String, kravidentType: Kravidentifikatortype, krav: KravLinje, corrID: String): HttpResponse {
	val request = makeStoppKravRequest(kravident, kravidentType)
	val response = skeClient.stoppKrav(request, corrID)
      
	if (!response.status.isSuccess()) {
	  databaseService.saveErrorMessageToDatabase(Json.encodeToString(request), response, krav, kravident)
	}

	return response
  }

  private suspend fun sendEndreKrav(kravident: String, kravidentType: Kravidentifikatortype, krav: KravLinje, corrID: String): Map<String, HttpResponse> {

	//skeClient.endreOppdragsGiversReferanse(lagNyOppdragsgiversReferanseRequest(linje), kravident, kravidentType)

	val endreRenterRequest = makeEndreRenteRequest(krav)
	val renteresponse = skeClient.endreRenter(endreRenterRequest, kravident, kravidentType, corrID)

	if (!renteresponse.status.isSuccess()) {
	  logger.info("FEIL I INNSENDING AV ENDRING AV RENTER PÅ LINJE ${krav.linjeNummer}: ${renteresponse.status} ${renteresponse.bodyAsText()}")
	  databaseService.saveErrorMessageToDatabase(Json.encodeToString(endreRenterRequest), renteresponse, krav, kravident)
	}

	val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
	val hovedstolResponse = skeClient.endreHovedstol(endreHovedstolRequest, kravident, kravidentType, corrID)

	if (!hovedstolResponse.status.isSuccess()) {
	  logger.info("FEIL I INNSENDING AV ENDRING AV HOVEDSTOL PÅ LINJE ${krav.linjeNummer}: ${hovedstolResponse.status} ${hovedstolResponse.bodyAsText()}")
	  databaseService.saveErrorMessageToDatabase(Json.encodeToString(endreHovedstolRequest), hovedstolResponse, krav, kravident)
	}

	val responseMap = mapOf(ENDRE_RENTER to renteresponse, ENDRE_HOVEDSTOL to hovedstolResponse)
	return responseMap

  }


  private suspend fun sendOpprettKrav(krav: KravLinje, substfnr: String,  corrID: String): HttpResponse {
	val opprettKravRequest = makeOpprettKravRequest(krav.copy(gjelderID =
	if (krav.gjelderID.startsWith("00")) krav.gjelderID else substfnr), databaseService.insertNewKobling(krav.saksNummer, corrID))
	val response = skeClient.opprettKrav(opprettKravRequest, corrID)

	if (!response.status.isSuccess()) {
	  logger.info("FEIL i innsending av NYTT PÅ LINJE ${krav.linjeNummer}: ${response.status}  ${response.bodyAsText()}")
	  databaseService.saveErrorMessageToDatabase(Json.encodeToString(opprettKravRequest), response, krav, "")
	}
	return response
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

	println("STARTER MOTTAKSSTATUS ${LocalDateTime.now()}")
	val krav =  databaseService.hentAlleKravSomIkkeErReskotrofort()

	println("SKAL OPPDATERE FOR ${krav.size} KRAV ${LocalDateTime.now()}")
	val result = krav.map {



	  var kravidentType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
	  var kravident = it.saksnummerSKE

	  if (it.saksnummerSKE.isEmpty()) {
		kravident = it.referanseNummerGammelSak
		kravidentType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
	  }
	  antall++
	  println(" HENTER MOTTAKSSTATUS FRA SKATT ${LocalDateTime.now()}")

	  val response = skeClient.getMottaksStatus(kravident, kravidentType)


	  println(" HENTET MOTTAKSSTATUS ${LocalDateTime.now()}")
	  if (response.status.isSuccess()) {
		try {
		  val mottaksstatus = response.body<MottaksStatusResponse>()
		  databaseService.updateStatus(mottaksstatus)
		  println("OPPDATERTE STATUS ${LocalDateTime.now()}")
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
	  println("GÅR TIL NESTE ${LocalDateTime.now()}")
	  "Status ok: ${response.status.value}, ${response.bodyAsText()}"
	}

	return result + "Antall behandlet  $antall, Antall feilet: $feil"
  }

  suspend fun hentValideringsfeil(): List<String> {
	val krav   = databaseService.getAlleKravMedValideringsfeil()


	val resultat = krav.map {
	  var kravidentType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
	  var kravident = it.saksnummerSKE

	  if (it.saksnummerSKE.isEmpty()) {
		kravident = it.referanseNummerGammelSak
		kravidentType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
	  }
	  val response = skeClient.getValideringsfeil(kravident, kravidentType)

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
