package sokos.skd.poc.service


import io.ktor.client.statement.*
import io.ktor.http.*
import sokos.skd.poc.client.SkeClient
import sokos.skd.poc.navmodels.DetailLine
import kotlin.math.roundToLong

class SkeService(
    private val skdClient: SkeClient,
    private val ftpService: FtpService = FtpService().apply {connect(fileNames = listOf("fil1.txt")) }
)
{
    fun sjekkOmNyFtpFil(): List<String> = FtpService().apply { connect() }.listFiles() //brukes for testing i postman


    suspend fun sendNyeFtpFilerTilSkatt(): List<HttpResponse> {
        val files =  ftpService.getFiles(::fileValidator)
        val results = mutableMapOf<FtpFil, MutableList<HttpResponse>>()
        val failedLines = mutableMapOf<Int, DetailLine>()

        files.forEach { file ->
            val responses = mutableListOf<HttpResponse>()

            file.detailLines.subList(0,1).forEach{
                val request = createRequest(it) //logge request om feiler?

                val response = when {
                    it.erStopp() -> skdClient.stoppKrav(request)
                    it.erEndring() -> skdClient.endreKrav(request)
                    else -> skdClient.opprettKrav(request)
                }

                println(response)
                responses.add(response)

                if(response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {  //legg object i feilliste
                    failedLines[file.detailLines.indexOf(it) + 1] = it
                    println("FAILED REQUEST: $request") //logge request?
                }
            }
            results[file] = responses
            handleAnyFailedLines(failedLines, file)
        }

        handleSentFiles(results)

        return results.map { it.value }.flatten()
    }

    private fun createRequest(line: DetailLine)= when {
        line.erStopp() -> {
            println("er stopp")
            lagStoppKravRequest(line)
        }
        line.erEndring() -> {
            println("er endre")
            lagEndreKravRequest(line)
        }
        else -> lagOpprettKravRequest(line)
    }

    private fun handleSentFiles(results: MutableMap<FtpFil, MutableList<HttpResponse>>){
        //flytte hele denne fila til sendt mappe?
        //fjerne evt linjer som faila og så flytte?
        results.forEach { entry ->
            val moveTo: Directories =
                if(entry.value.any { it.status != HttpStatusCode.OK && it.status != HttpStatusCode.Created }) Directories.FAILED else Directories.SENDT
            ftpService.moveFile(entry.key.name, Directories.OUTBOUND, moveTo)
        }
    }

    private fun handleAnyFailedLines(failedLines: MutableMap<Int, DetailLine>, file: FtpFil){
        if(failedLines.isNotEmpty()) {
            println("Number of failed lines: ${failedLines.size}")
            //oppretter ny fil som inneholder de linjene som har feilet
            val failedContent: List<String> = failedLines.map { entry -> file.content[entry.key] }
            ftpService.createFile("${file.name}-FailedLines", failedContent, Directories.FAILED)
            //opprette sak i gosys elns
        }
    }

}
private fun DetailLine.erEndring(): Boolean {
    return referanseNummerGammelSak.isNotEmpty() && !erStopp()
}

private fun DetailLine.erStopp(): Boolean {
    return belop.roundToLong() == 0L
}