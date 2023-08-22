package sokos.skd.poc

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

class SkdService(
    private val skdClient: SkdClient
)
{
    suspend fun sjekkOmNyFilOgSendTilSkatt(antall: Int) = runBlocking {
        val data: List<String> = if (antall.equals(1)) testData1() else testData101()

        val trekklisteObj = mapFraNavTilSkd(data).subList(0,antall.coerceAtMost(data.size))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()

        trekklisteObj.forEach {
            val kravRequest = gson.toJson(it)
            try {
                println("Forsøker sende: $it")
                val response = skdClient.opprettKrav(kravRequest)
                println("sendt: ${kravRequest}, Svaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }
        }

    }
}

