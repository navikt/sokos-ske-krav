import org.junit.jupiter.api.Test
import sokos.skd.poc.readFileFromOS

class ReadFileTest {

    @Test
    fun lesinnHeleFila() {
        var liste = readFileFromOS("/Users/d149678/Documents/jobb/SKATT-POC/eksempelfil_TBK.txt")
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