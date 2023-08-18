package sokos.skd.poc

import com.google.gson.GsonBuilder
import java.time.LocalDate
import java.time.LocalDateTime

class SkdService {

    suspend fun runjob(filnavn:String) {
        val trekklisteObj = mapFraNavTilSkd(readFileFromFS(filnavn.asResource() ))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()

        val skdClient = SkdClient(readProperty("SKD_REST_URL", ""))
        trekklisteObj.forEach {
            val kravRequest = gson.toJson(it)
            try {
                skdClient.doPost("innkrevingsoppdrag", kravRequest)
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}")
            }

        }

    }
}