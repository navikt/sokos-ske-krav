package sokos.ske.krav.service


import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.navmodels.FailedLine
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import kotlin.math.roundToLong

class SkeService(
    private val skeClient: SkeClient,
    private val ftpService: FtpService = FtpService().apply { connect(fileNames = listOf("fil1.txt")) }
) {
    private val log = KotlinLogging.logger {}
    fun sjekkOmNyFtpFil(): List<String> = FtpService().apply { connect() }.listFiles() //brukes for testing i postman


    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        println("Starter service")
        val files = ftpService.getFiles(::fileValidator)

        val responses = files.map { file ->
            val svar: List<Pair<DetailLine, HttpResponse>> = file.detailLines.subList(0, 1).map {

                val response = when {
                    it.erStopp() -> skeClient.stoppKrav(lagStoppKravRequest(it))
                    it.erEndring() -> skeClient.endreKrav(lagEndreKravRequest(it))
                    else -> skeClient.opprettKrav(lagOpprettKravRequest(it))
                }

                if (it.erNyttKrav()) {
                    val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                    //putte i database og gjøre ting...
                }

                println(response)

                if (response.status.isError()) {  //legg object i feilliste
                    println("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}") //logge request?
                    log.info("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}") //logge request?
                }
                it to response
            }

            val (httpResponseOk, httpResponseFailed) = svar.partition { it.second.status.isError() }
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
