package sokos.ske.krav


import kotlinx.coroutines.delay
import java.io.File
import java.io.InputStream
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


internal const val DEFAULT_RETRY_ATTEMPTS = 5
internal const val DEFAULT_DELAY_DURATION = 500L


fun readFileFromFS(file: String): List<String> {
    val pn = URI(file)
    pn.normalize()
    return File(URI(file)).readLines()
}

object Utils {
    suspend fun <T> retry(
        numberOfTries: Int = DEFAULT_RETRY_ATTEMPTS,
        interval: Duration = DEFAULT_DELAY_DURATION.milliseconds,
        block: suspend () -> T
    ): T {
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

fun prefixString(field: String, len: Int, prefix: String): String {
    var result: String = field
    while (result.length < len) (prefix + result).also { result = it }
    return result.substring(0, len)
}

fun prefixString(field: Double, len: Int, prefix: String): String  {
    val str: String = field.toString().let {
        val pos = it.indexOf(".")
        if (pos > -1) {
            when (it.length - pos) {
                1 -> it.dropLast(1)+"00"
                2 -> it.dropLast(2) + it.drop(pos + 1)+"0"
                3 -> it.dropLast(3) + it.drop(pos + 1)
                else -> {
                    println("Skal ikke skje")
                    "000000000000"
                    //TODO kaste exception ??
                }
            }
        }else it
    }
    return prefixString(str, 11, "0")
}


fun prefixString(field: Int, len: Int, prefix: String) = sokos.ske.krav.prefixString(field.toString(), len, prefix)

fun suffixStringWithSpace(field: String, len: Int): String {
    var result: String = field
    while (result.length < len) (result + " ").also { result = it }
    return result.substring(0, len)
}

fun String.asResource(): InputStream = object {}.javaClass.classLoader.getResourceAsStream(this)!!




