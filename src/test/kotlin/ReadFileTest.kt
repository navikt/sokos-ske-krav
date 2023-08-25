import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine
import sokos.skd.poc.parseFRtoDataDetailLineClass
import sokos.skd.poc.parseFRtoDataFirsLineClass
import sokos.skd.poc.parseFRtoDataLastLIneClass
import sokos.skd.poc.readFileFromFS
import java.time.LocalDateTime

internal class ReadFileTest : FunSpec({

    val liste = readFileFromFS("101.txt".asResource())

    test("lesinnHeleFila") {
        println("101.txt".asResource())
        println("Antall i lista ${liste.size}")
        println("Første linje: ${liste.first()}")
        liste.forEach { println(it) }
        println("Siste linje: ${liste.last()}")
    }
    //Users/d149678/IdeaProjects/sokos-skd-poc/build/resources/test


    test("lesInnStartLinjeTilclass") {
        val expected = FirstLine(
            transferDate = LocalDateTime.of(2023, 5, 26, 22, 13, 40),
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
