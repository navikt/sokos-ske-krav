package sokos.skd.poc

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Test
import sokos.skd.poc.skdmodels.AvskrivingRequest
import sokos.skd.poc.skdmodels.EndringRequest
import sokos.skd.poc.skdmodels.OpprettInnkrevingsoppdragRequest
import java.time.LocalDate
import java.time.LocalDateTime


class MapFraNavLinjerTilSkdModellKtTest {

    @Test
    fun mapperTestRecourceFilTest() {
        val filnavn = "101.txt"
        //val navKravliste = mapFraFRTilDetailAndValidate(readFileFromFS(filnavn.asResource()))
        val navKravliste = mapFraFRTilDetailAndValidate(testData101())
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()
        val nye = mutableListOf<OpprettInnkrevingsoppdragRequest>()
        val endrede = mutableListOf<EndringRequest>()
        val stoppede = mutableListOf<AvskrivingRequest>()
        navKravliste.forEach { detail ->
            println(detail)
            when {
                detail.erStopp() -> {

                    val stopp = lagStoppKravRequest(detail).also(::println)
                    stoppede.add(stopp)
                }

                detail.erEndring() -> {
                    val endre = lagEndreKravRequest(detail).also(::println)
                    endrede.add(endre)
                }

                else -> {
                    val ny = lagOpprettKravRequest(detail).also(::println)
                    nye.add(ny)
                    println("- ${gson.toJson(ny)}")
                }
            }
        }

        kotlin.test.assertTrue(nye.size.equals(91), "Antall nye matcher ikke")
        kotlin.test.assertTrue(endrede.size.equals(8), "Antall endrede matcher ikke")
        kotlin.test.assertTrue(stoppede.size.equals(2), "Antall stoppede matcher ikke")
        println("Nye: ${nye.size}, endrede: ${endrede.size}, Stopp: ${stoppede.size}")
        //val trekklisteJson = gson.toJson(trekklisteObj).also { println(it) }

    }

}

//class LocalDateTypeAdapter : JsonSerializer<LocalDate?>, JsonDeserializer<LocalDate?> {
//    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
//
//    @Throws(JsonParseException::class)
//    override fun deserialize(
//        json: JsonElement, typeOfT: Type?,
//        context: JsonDeserializationContext?
//    ): LocalDate {
//        return LocalDate.parse(json.asString, formatter)
//    }
//
//    override fun serialize(
//        src: LocalDate?,
//        typeOfSrc: java.lang.reflect.Type?,
//        context: JsonSerializationContext?
//    ): JsonElement {
//        return JsonPrimitive(src?.format(formatter))
//    }
//
//}

//class LocalDateTimeTypeAdapter : JsonSerializer<LocalDateTime?>, JsonDeserializer<LocalDateTime?> {
//    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//
//    @Throws(JsonParseException::class)
//    override fun deserialize(
//        json: JsonElement, typeOfT: Type?,
//        context: JsonDeserializationContext?
//    ): LocalDateTime {
//        return LocalDateTime.parse(json.asString, formatter)
//    }
//
//    override fun serialize(
//        src: LocalDateTime?,
//        typeOfSrc: java.lang.reflect.Type?,
//        context: JsonSerializationContext?
//    ): JsonElement {
//        return JsonPrimitive(src?.format(formatter))
//    }
//
//}