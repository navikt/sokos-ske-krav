import org.junit.jupiter.api.Test
import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine
import sokos.skd.poc.parseFRtoDataDetailLineClass
import sokos.skd.poc.parseFRtoDataFirsLineClass
import sokos.skd.poc.parseFRtoDataLastLIneClass
import sokos.skd.poc.readFileFromFS
import java.net.URL
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ReadFileTest {

    var liste = readFileFromFS("eksempelfil_TBK.txt".asResource())
    @Test
    fun lesinnHeleFila() {
        println("Antall i lista ${liste.size}")
        println("Første linje: ${liste.first()}")
        liste.forEach { println(it) }
        println("Siste linje: ${liste.last()}")
    }

    @Test
    fun lesInnStartLinjeTilclass() {
        val expected = FirstLine(
            transferDate = LocalDateTime.of(2023, 5, 26, 22, 13, 40),
            sender = "OB04"
        )
        val startlinje: FirstLine = parseFRtoDataFirsLineClass(liste.first())
        println(startlinje)
        assertEquals(expected.toString(), startlinje.toString())

    }

    @Test
    fun lesInnDetailLinjerTilClass() {
        val subList = liste.subList(1, liste.lastIndex)
        subList.forEach {
            println(it)
            println(parseFRtoDataDetailLineClass(it))
        }
    }

    @Test
    fun lesInnSluttLineTilClass() {
        val sluttlinje: LastLine = parseFRtoDataLastLIneClass(liste.last()).also { println(liste.last()) }
        println("sluttlinje antall trans linjer: ${sluttlinje.numTransactionLines}")
        assert(sluttlinje.numTransactionLines.equals(101)) {"feilet i test"}
        println(sluttlinje)
    }

    @Test
    fun sjekkAtSumStemmerMedSisteLinje() {
        var sumAlleLinjer = 0.0
        var sumAlleRenter = 0.0
        liste.subList(1, liste.lastIndex).forEach {
            parseFRtoDataDetailLineClass(it)
                .let {
                    sumAlleRenter += it.belopRente
                    sumAlleLinjer += it.belop
                }
        }
        assertEquals(parseFRtoDataLastLIneClass(liste.last()).sumAllTransactionLines, sumAlleLinjer + sumAlleRenter)
    }
}

fun String.asResource(): URL = object {}.javaClass.classLoader.getResource(this)!!
