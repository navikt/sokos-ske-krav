package sokos.skd.poc

import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import sokos.skd.poc.database.DataSource
import sokos.skd.poc.navmodels.DetailLine
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
                when {
                    it.erStopp() -> {
                         response = skdClient.stoppKrav(lagStoppKravRequest(it))
                    }
                    it.erEndring() -> {
                        response = skdClient.endreKrav((lagEndreKravRequest(it)))
                    }
                    else -> {
                        response = skdClient.opprettKrav(lagOpprettKravRequest(it))
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

}

private fun DetailLine.erEndring(): Boolean {
    return !referanseNummerGammelSak.isNullOrEmpty() && !erStopp()
}

private fun DetailLine.erStopp(): Boolean {
    return belop.roundToLong().equals(0)
}

