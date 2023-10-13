package sokos.ske.krav.service


import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.requests.AvskrivingRequest
import sokos.ske.krav.skemodels.requests.EndringRequest
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.skemodels.responses.SokosValideringsfeil
import sokos.ske.krav.skemodels.responses.ValideringsfeilResponse
import kotlin.math.roundToLong

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRE_KRAV = "ENDRE_KRAV"
const val STOPP_KRAV = "STOPP_KRAV"

class SkeService(
    private val skeClient: SkeClient,
    private val dataSource: PostgresDataSource = PostgresDataSource(),
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


    suspend fun testListFiles(directory: String): List<String> { return ftpService.listAllFiles(directory)}
    suspend fun testFtp(): List<FtpFil> {
        return ftpService.getValidatedFiles(::fileValidator)

    }

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        logger.info { "Starter skeService SendNyeFtpFilertilSkatt." }
        val files = ftpService.getValidatedFiles(::fileValidator)
        logger.info { "Antall filer i kjøring ${files.size}" }

        val responses = files.map { file ->
            val svar: List<Pair<DetailLine, HttpResponse>> = file.detailLines.map {


                val kravTable = kravService.hentSkeKravident2(it.saksNummer)
                var kravident = kravService.hentSkeKravident(it.saksNummer)
                var request: String

                println("KRAVTABLE SIZE: ${kravTable.size} | SAKSNUMMER: ${it.saksNummer}")

                if (kravident.isEmpty() && !it.erNyttKrav()) {

                    println("SAKSNUMMER: ${it.saksNummer}")
                    println(kravTable)

                    println("Hva faen gjør vi nå :( ")
                    //hva faen gjør vi nå??
                    //Dette skal bare skje dersom dette er en endring/stopp av et krav sendt før implementering av denne appen.
                }

                val response = when {
                    it.erStopp() -> {
                        println("KRAVIDENT: $kravident")
                        request = toJson(AvskrivingRequest.serializer(),lagStoppKravRequest(kravident) )
                        println("REQUEST: $request")
                        skeClient.stoppKrav(request)
                    }
                    it.erEndring() -> {
                        request = toJson(EndringRequest.serializer(), lagEndreKravRequest(it, kravident) )
                        skeClient.endreKrav(request)
                    }
                    it.erNyttKrav() -> {
                        request = toJson(OpprettInnkrevingsoppdragRequest.serializer(), lagOpprettKravRequest(it.copy(saksNummer = kravService.lagreNyKobling(it.saksNummer))) )
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

            val (httpResponseOk, httpResponseFailed) = svar.partition { it.second.status.isSuccess()  }

          //  val failedLines = httpResponseFailed.map { FailedLine(file, parseDetailLinetoFRData(it.first), it.second.status.value.toString(), it.second.bodyAsText()) }
         //   handleAnyFailedLines(failedLines)

            svar
        }

        files.forEach { file ->  ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND) }

        return responses.map { it.map { it.second } }.flatten()
    }


    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        var antall = 0
        var feil = 0
        val result = kravService.hentAlleKravSomIkkeErReskotrofort().map {
            antall += 1
            logger.info { "Logger (Status start): ${it.saksnummer_ske}" }
            if(it.saksnummer_ske.isBlank()){
                println("STATUS: ${it.status} , FILDATA NAV: ${it.fildata_nav}")
                println("KRAV ID: ${it.krav_id} , KRAVTYPE: ${it.kravtype}, SAKSNUMMER NAV: ${it.saksnummer_nav}")
            }
            val response = skeClient.hentMottaksStatus(it.saksnummer_ske)
            logger.info { "Logger (Status hentet): ${it.saksnummer_ske}" }
            if (response.status.isSuccess()) {
                logger.info { "Logger (Status success): ${it.saksnummer_ske}" }
                try {
                    val body = response.bodyAsText()
                    logger.info { "Logger status body: $body" }
                    val mottaksstatus = Json.decodeFromString<MottaksstatusResponse>(body)
                    logger.info { "Logger mottaksresponse: $mottaksstatus, Body: ${body}" }
                    kravService.oppdaterStatus(mottaksstatus)
                    logger.info { "Logger (Status oppdatert): ${it.saksnummer_ske}" }
                    "Status OK: ${response.bodyAsText()}"
                } catch (e: Exception) {
                    feil += 1
                    logger.error { "Logger Exception: ${e.message}" }
                    throw e
                }
            }
            logger.info { "Logger (Status ferdig): ${it.saksnummer_ske}" }
            "Status ok: ${response.status.value}, ${response.bodyAsText()}"
        }
        logger.info { "Loger status: ferdig  (antall $antall, feilet: $feil) commit og closer connectin" }
        val r = result + "Antall behandlet  $antall, Antall feilet: $feil"
        return r
    }

    suspend fun hentValideringsfeil(): List<String> {
        val resultat = kravService.hentAlleKravMedValideringsfeil().map {
            logger.info { "Logger (Validering start): ${it.saksnummer_ske}" }
            val response = skeClient.hentValideringsfeil(it.saksnummer_ske)
            logger.info { "Logger (Validering hentet): ${it.saksnummer_ske}" }
            if (response.status.isSuccess()) {
                logger.info { "Logger (validering success): ${it.saksnummer_ske}" }
                val resObj = Json.parseToJsonElement(response.bodyAsText())

                logger.info { "ValideringsObj: $resObj" }

                val valideringsfeilResponse = SokosValideringsfeil(
                    kravidSke = it.saksnummer_ske,
                    valideringsfeilResponse = Json.decodeFromString<ValideringsfeilResponse>(response.bodyAsText())
                )
                logger.info { "Serialisering gikk fint: ${valideringsfeilResponse.kravidSke}, ${valideringsfeilResponse.valideringsfeilResponse}" }

                kravService.lagreValideringsfeil(valideringsfeilResponse)
                //lag ftpfil og  kall handleAnyFailedFiles
                "Status OK: ${response.bodyAsText()}"
            } else {
                logger.info { "Logger (Fikk ikke hentet valideringsfeil for:  ${it.saksnummer_ske}, Status: ${response.status.value})" }
                "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
            }
        }
        if (resultat.isEmpty()) logger.info { "HENTVALIDERINGSFEIL: Ingen krav å hente validering for" }
        else logger.info { "HENTVALIDERINGSFEIL: Det er ${resultat.size} krav det er hentet valideringsfeil for" }
        return resultat
    }


    private fun handleAnyFailedLines(failedLines: List<FailedLine>) {

        if (failedLines.isNotEmpty()) {
            println("Number of failed lines: ${failedLines.size}")
            val failedContent: String =
                failedLines.joinToString("\n") { line -> "${line.line}-${line.error},${line.message} " }


        }

    }

}

private fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
private fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
private fun DetailLine.erStopp() = (belop.roundToLong() == 0L)
fun HttpStatusCode.isError() = (this != HttpStatusCode.OK && this != HttpStatusCode.Created)
