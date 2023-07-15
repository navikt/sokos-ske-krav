import com.google.gson.*
import io.ktor.util.reflect.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sokos.skd.poc.readFileFromFS
import sokos.skd.poc.readFileFromFtp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong


class MapFraNavLinjerTilSkdModellKtTest {

    @Test
    fun mapperTestRecourceFilTest() {
        val trekklisteObj = mapFraNavTilSkd(readFileFromFS("eksempelfil_TBK.txt".asResource() ))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()
        val trekklisteJson = gson.toJson(trekklisteObj)

    }
    @Test
    fun MapperFtpFilTest() {
        val trekklisteObj = mapFraNavTilSkd(readFileFromFtp("eksempelfil_TBK.txt"))
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .create()
            val trekklisteJson = gson.toJson(trekklisteObj)
        println(trekklisteJson)
    }
    @Test
    fun testa() {
        val dbl1 = 123.45
        val dbl2 = 123.55
        val lng1 =  dbl1.roundToLong()
        val lng2 =  dbl2.roundToLong()
        println("dbl: $dbl1, lng: $lng1")
        println("dbl: $dbl2, lng: $lng2")

    }
}

class LocalDateTypeAdapter : JsonSerializer<LocalDate?>, JsonDeserializer<LocalDate?> {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDate {
        return LocalDate.parse(json.getAsString(), formatter)
    }

    override fun serialize(
        src: LocalDate?,
        typeOfSrc: java.lang.reflect.Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(formatter))
    }

}class LocalDateTimeTypeAdapter : JsonSerializer<LocalDateTime?>, JsonDeserializer<LocalDateTime?> {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime {
        return LocalDateTime.parse(json.getAsString(), formatter)
    }

    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: java.lang.reflect.Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(formatter))
    }

}