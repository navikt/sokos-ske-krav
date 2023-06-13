import org.junit.jupiter.api.Test
import sokos.skd.poc.readFileFromOS
import java.net.URL

class ReadFileTest {

    @Test
    fun lesinnHeleFila() {
        var liste = readFileFromOS("eksempelfil_TBK.txt".asResource().path.also { println(it) })
        println("Antall i lista ${liste.size}")
        println("Første linje: ${liste.first()}")
        println("Siste linje: ${liste.last()}")
    }

    fun lesStartLinja(filLinjer: List<String>): String {
        return filLinjer.first()
    }

    fun lesSluttLinja(filLinjer: List<String>): String {
        return filLinjer.last()
    }
}

fun String.asResource(): URL = object {}.javaClass.classLoader.getResource(this)!!
