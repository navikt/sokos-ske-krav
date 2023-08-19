package sokos.skd.poc

import com.google.gson.GsonBuilder
import java.time.LocalDate
import java.time.LocalDateTime

class SkdService {

    suspend fun runjob(filnavn:String) {
        val trekklisteObj = mapFraNavTilSkd(liste())
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()

        val skdClient = SkdClient(readProperty("SKD_REST_URL", ""))
        trekklisteObj.forEach {
            val kravRequest = gson.toJson(it)
            try {
                println("Forsøker sende: $it")
                val response = skdClient.doPost("innkrevingsoppdrag", kravRequest)
                println("sendt: ${kravRequest}, Svaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }

        }

    }
}

private fun liste(): List<String> {
    var l: List<String>
    l = ArrayList()
    l.add("001020230526221340OB04")
    l.add("00300000001OB040000520015    0000412320020230524123456789012022070120221031PE AP                     2023052448034819T                      0000000000000000000000")
    l.add("004020230526221340OB04     00000001000000004123200")
    return l
}