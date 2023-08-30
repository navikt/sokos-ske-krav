package sokos.skd.poc

import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import sokos.skd.poc.database.DataSource
import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.service.Directories
import sokos.skd.poc.service.FtpService
import kotlin.math.roundToLong

class SkdService(
    private val dataSource: DataSource,
    private val skdClient: SkdClient
)
{
    suspend fun sjekkOmNyFilOgSendTilSkatt(antall: Int) = runBlocking {
        val data: List<String> = if (antall.equals(1)) testData1() else testData101()
        var response: HttpResponse
        val navDetailLines = mapFraFRTilDetailAndValidate(data).subList(0, antall.coerceAtMost(data.size))

        navDetailLines.forEach {
            try {
                println("Forsøker sende: $it")
                response = when {
                    it.erStopp() -> {
                        skdClient.stoppKrav(lagStoppKravRequest(it))
                    }

                    it.erEndring() -> {
                        skdClient.endreKrav((lagEndreKravRequest(it)))
                    }

                    else -> {
                        skdClient.opprettKrav(lagOpprettKravRequest(it))
                    }
                }
                println("sendt: ${it},\nSvaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")

                //legg object i feilliste
            }
            //Hva gjør vi hvis feilliste ikke wer tom
        }
    }

    fun sjekkOmNyFtpFil(): List<String> = FtpService().apply { connect() }.listFiles()

    suspend fun sendNyeFtpFilerTilSkatt(): MutableList<HttpClientCall> {
        val ftpService: FtpService = FtpService().apply { connect(fileNames = listOf("eksempelfil_TBK.txt")) }
        val requests = ftpService.listFiles().map { ftpService.downloadFtpFile(it, Directories.OUTBOUND) }.flatMap { it.skeRequests }
        println("flatmap size: ${requests.size}")

        val responses = mutableListOf<HttpClientCall>()

        requests.first().apply {
            responses.add (skdClient.opprettKrav(this.toString()).request.call )
        }

        println("sendte krav")

        return responses
    }

}

private fun DetailLine.erEndring(): Boolean {
    return referanseNummerGammelSak.isNotEmpty() && !erStopp()
}

private fun DetailLine.erStopp(): Boolean {
    return belop.roundToLong() == 0L
}

