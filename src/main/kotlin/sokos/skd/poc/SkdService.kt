package sokos.skd.poc

import com.google.gson.GsonBuilder
import io.ktor.util.*
import java.time.LocalDate
import java.time.LocalDateTime

class SkdService {

    @OptIn(InternalAPI::class)
    suspend fun sjekkOmNyFilOgSendTilSkatt(antall: Int) {
        var data: List<String>
        if (antall.equals(1)) data = testData1()
        else data = testData101()

        val trekklisteObj = mapFraNavTilSkd(data).subList(0,antall.coerceAtMost(data.size))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()

        val skdClient = SkdClient(readProperty("SKD_REST_URL", ""))
        trekklisteObj.forEach {
            val kravRequest = gson.toJson(it)
            return try {
                println("Forsøker sende: $it")
                val response = skdClient.doPost("innkrevingsoppdrag", kravRequest)
                println("sendt: ${kravRequest}, Svaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }
        }

    }
}

