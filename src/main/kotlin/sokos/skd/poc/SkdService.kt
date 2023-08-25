package sokos.skd.poc

import com.google.gson.GsonBuilder
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import sokos.skd.poc.database.DataSource
import sokos.skd.poc.navmodels.DetailLine
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToLong

class SkdService(
    private val dataSource: DataSource,
    private val skdClient: SkdClient
) {
    suspend fun sjekkOmNyFilOgSendTilSkatt(antall: Int) = runBlocking {
        val data: List<String> = if (antall.equals(1)) testData1() else testData101()
        var response: HttpResponse
        val navDetailLines = mapFraFRTilDetailAndValidate(data).subList(0, antall.coerceAtMost(data.size))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()

        navDetailLines.forEach {
            try {
                println("Forsøker sende: $it")
                when {
                    it.erStopp() -> {
                         response = skdClient.stoppKrav(gson.toJson(lagStoppKravRequest(it)))
                    }
                    it.erEndring() -> {
                        response = skdClient.endreKrav(gson.toJson(lagEndreKravRequest(it)))
                    }
                    else -> {
                        response = skdClient.opprettKrav(gson.toJson(lagOpprettKravRequest(it)))
                    }
                }
                println("sendt: ${it},\nSvaret er: $response")
            } catch (e: Exception) {
                println("funka Ikke: ${e.message}, \n ${e.stackTraceToString()}")
            }
        }
    }

}

fun DetailLine.erEndring(): Boolean {
    return !referanseNummerGammelSak.isNullOrEmpty() && !erStopp()
}

fun DetailLine.erStopp(): Boolean {
    return belop.roundToLong().equals(0L)
}

