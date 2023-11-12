package sokos.ske.krav

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.LastLine
import sokos.ske.krav.util.FilParser
import java.io.File
import java.net.URI

internal class ParserTest : FunSpec({
    val liste = readFileFromFS("fil1.txt".asResource())
    val parser = FilParser(liste)

    test("lesInnStartLinjeTilclass") {
        val expected = FirstLine(
            transferDate = "20230526221340",
            sender = "OB04"
        )
        val startlinje: FirstLine = parser.parseForsteLinje()
        startlinje.toString() shouldBe expected.toString()
    }

    test("lesInnSluttLineTilClass") {
        val sluttlinje: LastLine = parser.parseSisteLinje()
        withClue({ "Antall transaksjonslinjer skal være 101: ${sluttlinje.numTransactionLines}" }) {
            sluttlinje.numTransactionLines shouldBe 101
        }
    }

    test("sjekkAtSumStemmerMedSisteLinje") {
        val sumBelopOgRenter = parser.parseKravLinjer().sumOf {
            it.belop + it.belopRente
        }
        parser.parseSisteLinje().sumAllTransactionLines shouldBe sumBelopOgRenter
    }
})

fun readFileFromFS(file: String): List<String> {
    val pn = URI(file)
    pn.normalize()
    return File(URI(file)).readLines()
}

fun String.asResource(): String = object {}.javaClass.classLoader.getResource(this)!!.toString()
