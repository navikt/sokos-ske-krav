package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isNyttKrav
import sokos.ske.krav.util.isStopp
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.makeEndreHovedstolRequest
import sokos.ske.krav.util.makeEndreRenteRequest
import sokos.ske.krav.util.makeOpprettKravRequest
import sokos.ske.krav.util.makeStoppKravRequest
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger


const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_RENTER = "ENDRE_RENTER"
const val ENDRE_HOVEDSTOL = "ENDRE_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

const val KRAV_SENDT = "KRAV_SENDT"
const val FEIL_MED_ENDRING = "FEIL MED DEN ANDRE ENDRINGEN"
const val FANT_IKKE_SAKSREF = "SKE FANT IKKE SAKSREF"
const val IKKE_RESKONTROFORT = "KRAV ER IKKE RESKONTROFØRT"

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

	  when {
		it.isStopp() -> {
		  val response = sendStoppKrav(kravident, kravidentType, it)
		  responsesMap[STOPP_KRAV] = response
		}

		it.isEndring() -> {
		  val endreResponses = sendEndreKrav(kravident, kravidentType, it)
		  responsesMap.putAll(endreResponses)
		}

		it.isNyttKrav() -> {
		  val response = sendOpprettKrav(it, substfnr)
		  if (response.status.isSuccess()) kravident = response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator

		  responsesMap[NYTT_KRAV] = response
		}
	  }

	  saveSentKravToDatabase(responsesMap, it, kravident)

	  allResponses.addAll(responsesMap.values)

	}

	val errors = allResponses.filter { !it.status.isSuccess() }
	if (errors.isNotEmpty()) {
	  //ALARM
	}



	return allResponses

  }

  private suspend fun saveErrorMessageToDatabase(request: String, response: HttpResponse, krav: KravLinje, kravident: String) {
	if (response.status.isSuccess()) return
	val kravidentSke = if (kravident == krav.saksNummer || kravident == krav.referanseNummerGammelSak) "" else kravident

	val feilResponse = response.body<FeilResponse>()

	if (feilResponse.status == 404) {
	  handleHvaFGjorViNaa(krav)
	}
	val feilmelding = FeilmeldingTable(
	  0L,
	  0L,
	  krav.saksNummer,
	  kravidentSke,
	  feilResponse.status.toString(),
	  feilResponse.detail,
	  request,
	  response.bodyAsText(),
	  LocalDateTime.now()
	)

	databaseService.saveFeilmelding(feilmelding)
  }

  private suspend fun determineStatus(allResponses: Map<String, HttpResponse>, response: HttpResponse): String {
    return if (allResponses.filter { resp -> resp.value.status.isSuccess() }.size == allResponses.size) KRAV_SENDT
	  else if (response.status.isSuccess()) FEIL_MED_ENDRING
	  else {
		val feilResponse = response.body<FeilResponse>()
		if (feilResponse.status == 404 && feilResponse.type.contains("innkrevingsoppdrag-eksisterer-ikke")) FANT_IKKE_SAKSREF
		else if (feilResponse.status == 409 && feilResponse.detail.contains("reskontroført")) IKKE_RESKONTROFORT
		else "UKJENT STATUS: ${allResponses.map { resp -> resp.value.status.value }}"
	  }

  }

  private suspend fun saveSentKravToDatabase(allResponses: Map<String, HttpResponse>, krav: KravLinje, kravident: String) {
	var kravidentToBeSaved = kravident

	allResponses.forEach { entry ->

	  Metrics.numberOfKravSent.inc()
	  Metrics.typeKravSent.labels(krav.stonadsKode).inc()

	  val statusString = determineStatus(allResponses, entry.value)

	  if (!krav.isNyttKrav() && kravidentToBeSaved == krav.referanseNummerGammelSak) kravidentToBeSaved = ""

	  databaseService.insertNewKrav(
		kravidentToBeSaved,
		krav,
		entry.key,
		statusString
	  )
	}

  }


  private suspend fun sendStoppKrav(kravident: String, kravidentType: Kravidentifikatortype, krav: KravLinje): HttpResponse {
	val request = makeStoppKravRequest(kravident, kravidentType)
	val response = skeClient.stoppKrav(request)
      
	if (!response.status.isSuccess()) {
	  saveErrorMessageToDatabase(Json.encodeToString(request), response, krav, kravident)
	}

	return response
  }

  private suspend fun sendEndreKrav(kravident: String, kravidentType: Kravidentifikatortype, krav: KravLinje): Map<String, HttpResponse> {

	//skeClient.endreOppdragsGiversReferanse(lagNyOppdragsgiversReferanseRequest(linje), kravident, kravidentType)

	val endreRenterRequest = makeEndreRenteRequest(krav)
	val renteresponse = skeClient.endreRenter(endreRenterRequest, kravident, kravidentType)

	if (!renteresponse.status.isSuccess()) {
	  logger.info("FEIL I INNSENDING AV ENDRING AV RENTER PÅ LINJE ${krav.linjeNummer}: ${renteresponse.status} ${renteresponse.bodyAsText()}")
	  saveErrorMessageToDatabase(Json.encodeToString(endreRenterRequest), renteresponse, krav, kravident)
	}

	val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
	val hovedstolResponse = skeClient.endreHovedstol(endreHovedstolRequest, kravident, kravidentType)

	if (!hovedstolResponse.status.isSuccess()) {
	  logger.info("FEIL I INNSENDING AV ENDRING AV HOVEDSTOL PÅ LINJE ${krav.linjeNummer}: ${hovedstolResponse.status} ${hovedstolResponse.bodyAsText()}")
	  saveErrorMessageToDatabase(Json.encodeToString(endreHovedstolRequest), hovedstolResponse, krav, kravident)
	}

	val responseMap = mapOf(ENDRE_RENTER to renteresponse, ENDRE_HOVEDSTOL to hovedstolResponse)
	return responseMap

  }


  private suspend fun sendOpprettKrav(krav: KravLinje, substfnr: String): HttpResponse {
	val opprettKravRequest = makeOpprettKravRequest(krav.copy(gjelderID = if (krav.gjelderID.startsWith("00")) krav.gjelderID else substfnr), databaseService.insertNewKobling(krav.saksNummer))
	val response = skeClient.opprettKrav(opprettKravRequest)

	if (!response.status.isSuccess()) {
	  logger.info("FEIL i innsending av NYTT PÅ LINJE ${krav.linjeNummer}: ${response.status}  ${response.bodyAsText()}")
	  saveErrorMessageToDatabase(Json.encodeToString(opprettKravRequest), response, krav, "")
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
		  logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}")
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
