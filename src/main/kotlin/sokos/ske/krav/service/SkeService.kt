package sokos.ske.krav.service


import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.hentAlleKravMedValideringsfeil
import sokos.ske.krav.database.Repository.hentAlleKravSomIkkeErReskotrofort
import sokos.ske.krav.database.Repository.lagreNyttKrav
import sokos.ske.krav.database.Repository.oppdaterStatus
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.navmodels.FailedLine
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import kotlin.math.roundToLong

class SkeService(
    private val skeClient: SkeClient,
    private val ftpService: FtpService = FtpService().apply { connect(fileNames = listOf("fil1.txt", "fil2.txt")) }
) {
    private val logger = KotlinLogging.logger {}
    private val dataSource: PostgresDataSource = PostgresDataSource()

    private inline fun <reified T> toJson(serializer: SerializationStrategy<T>, body: T) =
        builder.encodeToJsonElement(serializer, body).toString()

    private val builder = Json {
        encodeDefaults = true
        explicitNulls = false
    }


    suspend fun testResponse() {
        val files = ftpService.getFiles(::fileValidator)
        files.forEach { file ->
            file.detailLines.subList(0, 10).forEach {
                try {
                    val response = skeClient.opprettKrav(lagOpprettKravRequest(it))
                    println(response.bodyAsText())
                } catch (e: Exception) {
                    println(e.message)
                }

            }
        }
    }

    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        println("Starter service")
        val files = ftpService.getFiles(::fileValidator)
        val connection = dataSource.connection

        val responses = files.map { file ->
            val svar: List<Pair<DetailLine, HttpResponse>> = file.detailLines.map {

                val response = when {
                    it.erStopp() -> skeClient.stoppKrav(lagStoppKravRequest(it))
                    it.erEndring() -> skeClient.endreKrav(lagEndreKravRequest(it))
                    else -> skeClient.opprettKrav(lagOpprettKravRequest(it))
                }

                if (response.status.isSuccess()) {
                    if (it.erNyttKrav()) {
                        val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                        connection.lagreNyttKrav(
                            kravident.kravidentifikator,
                            toJson(OpprettInnkrevingsoppdragRequest.serializer(), lagOpprettKravRequest(it)),
                            parseDetailLinetoFRData(it),
                            it
                        )
                        connection.commit()
                    }
                } else {
                    logger.error("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}")
                }
                it to response
            }
            connection.close()

            val (httpResponseOk, httpResponseFailed) = svar.partition { it.second.status.isSuccess() }
            val failedLines = httpResponseFailed.map { FailedLine(it.first, it.second.status, it.second.bodyAsText()) }
            handleAnyFailedLines(failedLines, file)
            svar
        }

        //handleSentFiles(responses)

        return responses.map { it.map { it.second } }.flatten()
    }

    private fun handleSentFiles(results: MutableMap<FtpFil, MutableList<HttpResponse>>) {
        //flytte hele denne fila til sendt mappe?
        //fjerne evt linjer som faila og så flytte?
        results.forEach { entry ->
            val moveTo: Directories =
                if (entry.value.any { it.status.isError() }) Directories.FAILED else Directories.SENDT
            ftpService.moveFile(entry.key.name, Directories.OUTBOUND, moveTo)
        }
    }

    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        val connection = dataSource.connection
        var antall = 0
        var feil = 0
        val result = connection.hentAlleKravSomIkkeErReskotrofort().map {
            antall += 1
            logger.info { "Logger (Status start): ${it.saksnummer_ske}" }
            val response = skeClient.hentMottaksStatus(it.saksnummer_ske)
            logger.info { "Logger (Status hentet): ${it.saksnummer_ske}" }
            if (response.status.isSuccess()) {
                logger.info { "Logger (Status success): ${it.saksnummer_ske}" }
                try {
                    val body = response.bodyAsText()
                    logger.info { "Logger status body: $body" }
                    val mottaksstatus = Json.decodeFromString<MottaksstatusResponse>(body)
                    logger.info { "Logger mottaksresponse: $mottaksstatus, Body: ${body}" }
                    connection.oppdaterStatus(mottaksstatus)
                    connection.commit()
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
        connection.close()
        val r = result + "Antall behandlet  $antall, Antall feilet: $feil"
        return r
    }

    suspend fun hentValideringsfeil(): List<String> {
        val resultat = dataSource.connection.hentAlleKravMedValideringsfeil().map {
            logger.info { "Logger (Validering start): ${it.saksnummer_ske}" }
            val response = skeClient.hentValideringsfeil(it.saksnummer_ske)
            logger.info { "Logger (Validering hentet): ${it.saksnummer_ske}" }
            if (response.status.isSuccess()) {
                logger.info { "Logger (validering success): ${it.saksnummer_ske}" }

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


    private fun handleAnyFailedLines(failedLines: List<FailedLine>, file: FtpFil) {
        if (failedLines.isNotEmpty()) {
            println("Number of failed lines: ${failedLines.size}")
            //oppretter ny fil som inneholder de linjene som har feilet
            val failedContent: List<String> = failedLines.map {
                parseDetailLinetoFRData(it.detailLine) + it.httpStatusCode.value
            }
            ftpService.createFile("${file.name}-FailedLines", failedContent, Directories.FAILED)
            //opprette sak i gosys elns
        }
    }

}

private fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
private fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
private fun DetailLine.erStopp() = (belop.roundToLong() == 0L)
fun HttpStatusCode.isError() = (this != HttpStatusCode.OK && this != HttpStatusCode.Created)
