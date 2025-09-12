package no.nav.sokos.ske.krav.util

import java.math.BigDecimal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parsing.ParseException

import no.nav.sokos.ske.krav.dto.nav.KontrollLinjeFooter
import no.nav.sokos.ske.krav.dto.nav.KontrollLinjeHeader

internal class FileParserTest :
    FunSpec({
        val altOkFil = TestUtilFunctions.getFileContent("AltOkFil.txt")

        test("Alle linjer skal være av type KravLinje") {
            FileParser.parseKravLinjer(altOkFil).size shouldBe 101
        }

        test("startlinje skal være av type KontrollLinjeHeader") {
            FileParser.parseKontrollLinjeHeader(altOkFil) shouldBe
                KontrollLinjeHeader(
                    transaksjonsDato = "20230526221340",
                    avsender = "OB04",
                )
        }

        test("sluttLinje skal være av type KontrollLinjeFooter") {
            FileParser.parseKontrollLinjeFooter(altOkFil) shouldBe
                KontrollLinjeFooter(
                    transaksjonTimestamp = "20230526221340",
                    avsender = "OB04",
                    antallTransaksjoner = 101,
                    sumAlleTransaksjoner = "2645917.40".toBigDecimal(),
                )
        }

        test("Ugyldig BigDecimal skal kaste ParseException") {
            shouldThrow<ParseException> {
                FileParser.parseKravLinjer(TestUtilFunctions.getFileContent("FeilIParsingBigDecimal.txt"))
            }
        }

        test("Ugyldig Int skal kaste ParseException") {
            shouldThrow<ParseException> {
                FileParser.parseKontrollLinjeFooter(TestUtilFunctions.getFileContent("FeiliParsingAvInt.txt"))
            }
        }

        test("Feil encoded Ø skal erstattes med Ø") {
            val kravMedFeilEncoding = TestUtilFunctions.getFileContent("FA_FO_Feilencoding.txt")
            FileParser.parseKravLinjer(kravMedFeilEncoding).filter { linje -> linje.kravKode == "FA FØ" }.size shouldBe 1
        }

        test("Hvis linje ikke har fremtidig ytelse skal den settes til 0") {
            val utenFremtidigYtelse = TestUtilFunctions.getFileContent("Fil-uten-fremtidigytelse.txt")
            FileParser.parseKravLinjer(utenFremtidigYtelse).run {
                first { it.saksnummerNav == "FinnesIkke" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer1" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer2" }.fremtidigYtelse shouldBe BigDecimal.ZERO

                count { it.fremtidigYtelse == BigDecimal.ZERO } shouldBe 3
                count { it.fremtidigYtelse != BigDecimal.ZERO } shouldBe 2
            }
        }
    })
