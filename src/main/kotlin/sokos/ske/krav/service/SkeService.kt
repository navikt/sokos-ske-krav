package sokos.ske.krav.service


import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.hentAlleKravData
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
    private val ftpService: FtpService = FtpService().apply { connect(fileNames = listOf("fil1.txt")) }
) {
    private val log = KotlinLogging.logger {}
    private val dataSource: PostgresDataSource = PostgresDataSource()

    private inline fun <reified T> toJson(serializer: SerializationStrategy<T>, body: T) = builder.encodeToJsonElement(serializer, body).toString()

    private val builder = Json {
        encodeDefaults = true
        explicitNulls = false
    }


    suspend fun testResponse(){
        val files = ftpService.getFiles(::fileValidator)
        files.forEach {file ->
            file.detailLines.subList(0, 10).forEach {
                try {
                    val response = skeClient.opprettKrav(lagOpprettKravRequest(it))
                    println(response.bodyAsText())
                }catch (e: Exception){
                    println(e.message)
                }

            }
        }
    }
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

                println("post er ok")
                if(response.status.isSuccess()){
                    if (it.erNyttKrav()) {
                        println("Nytt Krav")
                        val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                        dataSource.connection.lagreNyttKrav( kravident.kravidentifikator,
                            toJson(OpprettInnkrevingsoppdragRequest.serializer(),lagOpprettKravRequest(it)),
                            parseDetailLinetoFRData(it),
                            it)
                        println("HentKravdata: ${dataSource.connection.hentAlleKravData().map { it.saksnummer_ske }}")
                    }
                }else{  //legg object i feilliste
                    println("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}") //logge request?
                    log.info("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}") //logge request?
                }
                it to response
            }

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

    suspend fun hentOgOppdaterMottaksStatus() =
        dataSource.connection.hentAlleKravSomIkkeErReskotrofort().map {
            println("(Status) Hentet ${it.saksnummer_ske}")
            val response = skeClient.hentMottaksStatus(it.saksnummer_ske)
            if (response.status.isSuccess()) {
                val mottaksstatus = Json.decodeFromString<MottaksstatusResponse>(response.bodyAsText())
                dataSource.connection.oppdaterStatus(mottaksstatus)
                "Status OK: ${response.bodyAsText()}"
            }
            "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
        }

    suspend fun hentValideringsfeil() =
        dataSource.connection.hentAlleKravMedValideringsfeil().map {
            println("Hentet ${it.saksnummer_ske}")
            val response = skeClient.hentValideringsfeil(it.saksnummer_ske)
            println("Resp: ${response.status.value}, ${response.bodyAsText()}")
            if (response.status.isSuccess()) "Status OK: ${response.bodyAsText()}"
            "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
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
