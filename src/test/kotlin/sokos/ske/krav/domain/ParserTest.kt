package sokos.ske.krav.domain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KontrollLinjeFooter
import sokos.ske.krav.domain.nav.KontrollLinjeHeader
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import java.io.File
import java.math.BigDecimal

internal class ParserTest :
    FunSpec({

        test("teste parsing av fil uten fremtidig ytelse") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}Fil-B-feil.txt")
            val parser = FileParser(liste)
            val kravlinjer = parser.parseKravLinjer()
            println(kravlinjer)
            kravlinjer.first { it.saksnummerNav == "FinnesIkke" }.fremtidigYtelse shouldBe BigDecimal.valueOf(0.0)
        }

        test("lesInnStartLinjeTilclass") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}AltOkFil.txt")
            val parser = FileParser(liste)
            val expected =
                KontrollLinjeHeader(
                    transaksjonsDato = "20230526221340",
                    avsender = "OB04",
                )
            val startlinje: KontrollLinjeHeader = parser.parseKontrollLinjeHeader()
            startlinje.toString() shouldBe expected.toString()
        }

        test("lesInnSluttLineTilClass") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}AltOkFil.txt")
            val parser = FileParser(liste)
            val sluttlinje: KontrollLinjeFooter = parser.parseKontrollLinjeFooter()
            withClue({ "Antall transaksjonslinjer skal være 101: ${sluttlinje.antallTransaksjoner}" }) {
                sluttlinje.antallTransaksjoner shouldBe 101
            }
        }

        test("sjekkAtSumStemmerMedSisteLinje") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}AltOkFil.txt")
            val parser = FileParser(liste)
            val sumBelopOgRenter =
                parser.parseKravLinjer().sumOf {
                    it.belop + it.belopRente
                }
            parser.parseKontrollLinjeFooter().sumAlleTransaksjoner shouldBe sumBelopOgRenter
        }
    })
