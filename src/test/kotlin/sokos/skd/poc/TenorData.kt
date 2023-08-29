package sokos.skd.poc

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class testjson {

    @Test
    fun lesOgSettInnDataFraCSV() {
        val fregdata = File("freg.csv".asResourceAsURL().toURI()).readLines()
        fregdata.forEach(::println)

    }

    @Test
    fun lesOgKonverterJson() {
        val jsonData: String = File("freg.json".asResourceAsURL().toURI()).readText()
        println(jsonData)
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()
        val td: TenorData = gson.fromJson(jsonData, sokos.skd.poc.TenorData::class.java)
        println(td)
    }
}

data class TenorData(
    val treff: Int,
    val dokumentListe: Array<DokumentListe>)

data class DokumentListe(val id: String)



