package sokos.skd.poc

import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

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
                         response = skdClient.stoppKrav(Json.encodeToJsonElement(lagStoppKravRequest(it)))
                    }
                    it.erEndring() -> {
                        response = skdClient.endreKrav(Json.encodeToJsonElement(lagEndreKravRequest(it)))
                    }
                    else -> {
                        response = skdClient.opprettKrav(Json.encodeToJsonElement(lagOpprettKravRequest(it)))
                    }
                }
                println("sendt: ${it},\nSvaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }
        }
    }

}

private fun DetailLine.erEndring(): Boolean {
    return !referanseNummerGammelSak.isNullOrEmpty() && !erStopp()
}

private fun DetailLine.erStopp(): Boolean {
    return belop.roundToLong().equals(0)
}

