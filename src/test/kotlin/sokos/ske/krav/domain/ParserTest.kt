package sokos.ske.krav.domain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KontrollLinjeFooter
import sokos.ske.krav.domain.nav.KontrollLinjeHeader
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import java.io.File

internal class ParserTest : FunSpec({
    val liste = fileAsList("${File.separator}FtpFiler${File.separator}AltOkFil.txt")
    val parser = FileParser(liste)

    test("lesInnStartLinjeTilclass") {
        val expected = KontrollLinjeHeader(
            transaksjonsDato = "20230526221340",
            avsender = "OB04"
        )
        val startlinje: KontrollLinjeHeader = parser.parseKontrollLinjeHeader()
        startlinje.toString() shouldBe expected.toString()
    }

    test("lesInnSluttLineTilClass") {
        val sluttlinje: KontrollLinjeFooter = parser.parseKontrollLinjeFooter()
        withClue({ "Antall transaksjonslinjer skal være 101: ${sluttlinje.antallTransaksjoner}" }) {
            sluttlinje.antallTransaksjoner shouldBe 101
        }
    }

    test("sjekkAtSumStemmerMedSisteLinje") {
        val sumBelopOgRenter = parser.parseKravLinjer().sumOf {
            it.belop + it.belopRente
        }
        parser.parseKontrollLinjeFooter().sumAlleTransaksjoner shouldBe sumBelopOgRenter
    }
})

