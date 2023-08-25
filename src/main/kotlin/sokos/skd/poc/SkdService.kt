package sokos.skd.poc

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement


class SkdService(
    private val skdClient: SkdClient
)
{
    suspend fun sjekkOmNyFilOgSendTilSkatt(antall: Int) = runBlocking {
        val data: List<String> = if (antall.equals(1)) testData1() else testData101()

        val trekklisteObj = mapFraNavTilSkd(data).subList(0,antall.coerceAtMost(data.size))


        trekklisteObj.forEach {
            val kravRequest = Json.encodeToJsonElement(it)
            try {
                println("Forsøker sende: $it")
                val response = skdClient.opprettKrav(kravRequest.toString())
                println("sendt: ${kravRequest}, Svaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }
        }
    }

}

