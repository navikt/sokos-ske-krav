package sokos.ske.krav

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.LastLine
import sokos.ske.krav.util.parseFRtoDataDetailLineClass
import sokos.ske.krav.util.parseFRtoDataFirsLineClass
import sokos.ske.krav.util.parseFRtoDataLastLineClass
import java.io.File
import java.net.URI

internal class ReadFileTest : FunSpec({
	val liste = readFileFromFS("fil1.txt".asResource())

	test("lesInnStartLinjeTilclass") {
		val expected = FirstLine(
			transferDate = "20230526221340",
			sender = "OB04"
		)
		val startlinje: FirstLine = parseFRtoDataFirsLineClass(liste.first())
		startlinje.toString() shouldBe expected.toString()
	}

	test("lesInnSluttLineTilClass") {
		val sluttlinje: LastLine = parseFRtoDataLastLineClass(liste.last()).also { println(liste.last()) }
		withClue({ "Antall transaksjonslinjer skal være 101: ${sluttlinje.numTransactionLines}" }) {
			sluttlinje.numTransactionLines shouldBe 101
		}
	}

	test("sjekkAtSumStemmerMedSisteLinje") {
		val sumBelopOgRenter = liste.subList(1, liste.lastIndex).sumOf {
			val parsed = parseFRtoDataDetailLineClass(it)
			parsed.belop + parsed.belopRente
		}
		parseFRtoDataLastLineClass(liste.last()).sumAllTransactionLines shouldBe sumBelopOgRenter
	}
})

fun readFileFromFS(file: String): List<String> {
	val pn = URI(file)
	pn.normalize()
	return File(URI(file)).readLines()
}

fun String.asResource(): String = object {}.javaClass.classLoader.getResource(this)!!.toString()
