
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

import sokos.skd.poc.mapFraNavTilSkd
import sokos.skd.poc.readFileFromFS

import kotlin.math.roundToLong


internal class MapFraNavLinjerTilSkdModellKtTest : FunSpec({


    test("mapperTestRecourceFilTest") {
        val filnavn = "101.txt"
        val trekklisteObj = mapFraNavTilSkd(readFileFromFS(filnavn.asResource()))


        trekklisteObj.forEach {
            println(it)
            println(Json.encodeToJsonElement(it).toString())
        }
        //val trekklisteJson = gson.toJson(trekklisteObj).also { println(it) }

    }

    /*    test("MapperFtpFilTest") {
            val trekklisteObj = mapFraNavTilSkd(readFileFromFtp("eksempelfil_TBK.txt"))
            val gson = GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .create()
                val trekklisteJson = gson.toJson(trekklisteObj)
            println(trekklisteJson)
        }*/

    test("testa") {
        val dbl1 = 123.45
        val dbl2 = 123.55
        val lng1 = dbl1.roundToLong()
        val lng2 = dbl2.roundToLong()
        println("dbl: $dbl1, lng: $lng1")
        println("dbl: $dbl2, lng: $lng2")

    }
})
