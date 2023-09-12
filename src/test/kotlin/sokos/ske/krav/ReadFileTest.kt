import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.navmodels.FirstLine
import sokos.ske.krav.navmodels.LastLine
import sokos.ske.krav.readFileFromFS
import sokos.ske.krav.service.lagOpprettKravRequest
import sokos.ske.krav.service.parseFRtoDataDetailLineClass
import sokos.ske.krav.service.parseFRtoDataFirsLineClass
import sokos.ske.krav.service.parseFRtoDataLastLIneClass

internal class ReadFileTest : FunSpec({

    val liste = readFileFromFS("101.txt".asResource())

    test("lesinnHeleFila") {
        println("101.txt".asResource())
        println("Antall i lista ${liste.size}")
        println("Første linje: ${liste.first()}")
        liste.forEach { println(it) }
        println("Siste linje: ${liste.last()}")
    }

    test("Kun detail lines"){
        println(liste[1])
        println(liste[liste.size-1])
        liste.subList(1, liste.size-1).forEach {
            println(it)
            val dl = parseFRtoDataDetailLineClass(it)
            println(lagOpprettKravRequest(dl)) }
    }





    test("lesInnStartLinjeTilclass") {
        val expected = FirstLine(
            transferDate = kotlinx.datetime.LocalDateTime.parse("2023-05-26T22:13:40"),
            sender = "OB04"
        )
        val startlinje: FirstLine = parseFRtoDataFirsLineClass(liste.first())
        println(startlinje)
        startlinje.toString() shouldBe expected.toString()

    }


    test("lesInnDetailLinjerTilClass") {
        liste.subList(1, liste.lastIndex).forEach {
            println(it)
            println(parseFRtoDataDetailLineClass(it))
        }
    }


    test("lesInnSluttLineTilClass") {
        val sluttlinje: LastLine = parseFRtoDataLastLIneClass(liste.last()).also { println(liste.last()) }
        println("sluttlinje antall trans linjer: ${sluttlinje.numTransactionLines}")
        withClue({ "Antall transaksjonslinjer skal være 101: ${sluttlinje.numTransactionLines}" }) {
            sluttlinje.numTransactionLines shouldBe 101
        }

        println(sluttlinje)
    }


    test("sjekkAtSumStemmerMedSisteLinje") {
        val sumBelopOgRenter = liste.subList(1, liste.lastIndex).sumOf {
            val parsed = parseFRtoDataDetailLineClass(it)
            parsed.belop + parsed.belopRente
        }

        parseFRtoDataLastLIneClass(liste.last()).sumAllTransactionLines shouldBe sumBelopOgRenter
    }
})

fun String.asResource(): String = object {}.javaClass.classLoader.getResource(this)!!.toString()
