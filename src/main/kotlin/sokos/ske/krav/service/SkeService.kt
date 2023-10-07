package sokos.ske.krav.service


import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.hentAlleKravData
import sokos.ske.krav.database.Repository.hentAlleKravMedValideringsfeil
import sokos.ske.krav.database.Repository.hentAlleKravSomIkkeErReskotrofort
import sokos.ske.krav.database.Repository.koblesakRef
import sokos.ske.krav.database.Repository.lagreNyKobling
import sokos.ske.krav.database.Repository.lagreNyttKrav
import sokos.ske.krav.database.Repository.lagreValideringsfeil
import sokos.ske.krav.database.Repository.oppdaterStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.replaceSaksnrInDetailline
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

    private inline fun <reified T> toJson(serializer: SerializationStrategy<T>, body: T) =
        builder.encodeToJsonElement(serializer, body).toString()

    @OptIn(ExperimentalSerializationApi::class)
    private val builder = Json {
        encodeDefaults = true
        explicitNulls = false
    }


    suspend fun testRepo() {
        val files = ftpService.getFiles(::fileValidator)

        files.map { file ->
            file.detailLines.subList(0, 10).forEach { line ->
                val response = skeClient.opprettKrav(lagOpprettKravRequest(line))
                val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                dataSource.connection.useAndHandleErrors { con ->
                    con.lagreNyttKrav(
                        kravident.kravidentifikator,
                        toJson(OpprettInnkrevingsoppdragRequest.serializer(), lagOpprettKravRequest(line)),
                        parseDetailLinetoFRData(line),
                        line,
                        NYTT_KRAV
                    )
                    con.commit()
                }

            }
        }
        val kravdata = dataSource.connection.useAndHandleErrors { con -> con.hentAlleKravData() }
        println("HentKravdata: ${kravdata}")
    }

    suspend fun testFtp(): MutableList<FtpFil> {
        return ftpService.getFiles(::fileValidator)

    }

    suspend fun sendFiler(): List<HttpResponse> {
        val files = ftpService.getFiles(::fileValidator)
        val results = mutableMapOf<FtpFil, MutableList<HttpResponse>>()
        val failedLines = mutableListOf<FailedLine>()
        val con = dataSource.connection
        files.forEach { file ->
            val responses = mutableListOf<HttpResponse>()

            file.detailLines.subList(0,25).forEachIndexed{index, line ->

                val response = when {
                    line.erStopp() -> skeClient.stoppKrav(lagStoppKravRequest(con.koblesakRef(line.saksNummer)))
                    line.erEndring() -> skeClient.endreKrav(lagEndreKravRequest(line, con.koblesakRef(line.saksNummer)))
                    else -> skeClient.opprettKrav(lagOpprettKravRequest(replaceSaksnrInDetailline(line, con.lagreNyKobling(line.saksNummer))))
                }

                responses.add(response)

                if(response.status.isSuccess()){
                    if(line.erNyttKrav()){
                        println(response.bodyAsText())
                        val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                        //putte i database og gjøre ting...
                    }
                } else{  //legg object i feilliste
                    failedLines.add(FailedLine(file, index))
                    println("FAILED REQUEST: $line, ERROR: ${response.bodyAsText()}") //logge request?
                }
            }
            results[file] = responses
            handleAnyFailedLines(failedLines)
        }

        handleSentFiles(results)

        return results.map { it.value }.flatten()

    }
    suspend fun sendNyeFtpFilerTilSkatt(antall: Int = 1): List<HttpResponse> {
        println("Starter service")
        val files = ftpService.getFiles(::fileValidator)
        logger.info { "Antall filer i kjøring ${files.size}" }
        val con = dataSource.connection
        val ant = if (antall == 0) 1 else antall

        val responses = files.map { file ->
            val svar: List<Pair<DetailLine, HttpResponse>> = file.detailLines.subList(0, ant).map {

                val response = when {
                    it.erStopp() -> skeClient.stoppKrav(lagStoppKravRequest(con.koblesakRef(it.saksNummer)))
                    it.erEndring() -> skeClient.endreKrav(lagEndreKravRequest(it, con.koblesakRef(it.saksNummer)))
                    else -> skeClient.opprettKrav(lagOpprettKravRequest(replaceSaksnrInDetailline(it, con.lagreNyKobling(it.saksNummer))))
                }

                if (response.status.isSuccess()) {
                    val kravident = Json.decodeFromString<OpprettInnkrevingsOppdragResponse>(response.bodyAsText())
                    con.lagreNyttKrav(
                        kravident.kravidentifikator,
                        toJson(OpprettInnkrevingsoppdragRequest.serializer(), lagOpprettKravRequest(it)),
                        parseDetailLinetoFRData(it),
                        it,
                        when {
                            it.erStopp() -> STOPP_KRAV
                            it.erEndring() -> ENDRE_KRAV
                            else -> NYTT_KRAV
                        }
                    )
                    con.commit()
                } else {
                    logger.error("FAILED REQUEST: $it, ERROR: ${response.bodyAsText()}")
                }
                it to response
            }

      /*      val (httpResponseOk, httpResponseFailed) = svar.partition { it.second.status.isSuccess() }
            val failedLines = httpResponseFailed.map { FailedLine(it.first, it.second.status, it.second.bodyAsText()) }
            handleAnyFailedLines(failedLines, file)*/
            svar
        }
        con.close()

        //handleSentFiles(responses)

        return responses.map { it.map { it.second } }.flatten()
    }

    private fun handleSentFiles(results: MutableMap<FtpFil, MutableList<HttpResponse>>) {
        //flytte hele denne fila til sendt mappe?
        //fjerne evt linjer som faila og så flytte?
        results.forEach { entry ->
            val moveTo: Directories =
                if (entry.value.any { it.status.isError() }) Directories.FAILED else Directories.OUTBOUND
            ftpService.moveFile(entry.key.name, Directories.INBOUND, moveTo)
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
        val con = dataSource.connection
        val resultat = con.hentAlleKravMedValideringsfeil().map {
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

                con.lagreValideringsfeil(valideringsfeilResponse)
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


    private fun handleAnyFailedLines(failedLines: MutableList<FailedLine>){

            if(failedLines.isNotEmpty()) {
                println("Number of failed lines: ${failedLines.size}")
                //oppretter ny fil som inneholder de linjene som har feilet
                val failedContent: String =
                    failedLines.joinToString("\n") { line -> line.file.content[line.lineNumber] }
                ftpService.createFile("${failedLines.first().file.name}-FailedLines", Directories.FAILED, failedContent)
                //opprette sak i gosys elns
            }

    }

}

private fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
private fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
private fun DetailLine.erStopp() = (belop.roundToLong() == 0L)
fun HttpStatusCode.isError() = (this != HttpStatusCode.OK && this != HttpStatusCode.Created)
