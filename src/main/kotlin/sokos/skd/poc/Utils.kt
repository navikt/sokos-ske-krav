package sokos.skd.poc

import com.google.gson.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.delay
import java.io.File
import java.io.InputStream
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


internal const val DEFAULT_RETRY_ATTEMPTS = 5
internal const val DEFAULT_DELAY_DURATION = 500L


fun readFileFromFtp(fileName: String): List<String> {
    val ftpClient = FtpClient()
    return ftpClient.downloadFileFromFtp(fileName)
}
fun readFileFromFS(file: String): List<String> {
    val pn: URI = URI(file)
    pn.normalize()
     return File(URI(file)).readLines()
}

object Utils {
    suspend fun <T> retry(numberOfTries: Int = DEFAULT_RETRY_ATTEMPTS, interval: Duration = DEFAULT_DELAY_DURATION.milliseconds, block: suspend () -> T): T {
        var attempt = 0
        var error: Throwable?
        do {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
            }
            attempt++
            delay(interval)
        } while (attempt < numberOfTries)

        throw error ?: IllegalStateException("Retry failed without error")
    }
}

class LocalDateTypeAdapter : JsonSerializer<LocalDate?>, JsonDeserializer<LocalDate?> {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDate {
        return LocalDate.parse(json.asString, formatter)
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
        return LocalDateTime.parse(json.asString, formatter)
    }

    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: java.lang.reflect.Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(formatter))
    }
}

fun String.asResource(): InputStream = object {}.javaClass.classLoader.getResourceAsStream(this)!!




