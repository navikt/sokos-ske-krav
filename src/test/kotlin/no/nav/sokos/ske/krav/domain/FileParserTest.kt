package no.nav.sokos.ske.krav.domain

import java.math.BigDecimal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parsing.ParseException

import no.nav.sokos.ske.krav.dto.nav.FileParser
import no.nav.sokos.ske.krav.dto.nav.KontrollLinjeFooter
import no.nav.sokos.ske.krav.dto.nav.KontrollLinjeHeader
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class FileParserTest :
    FunSpec({
        val altOkFil = getFileContent("AltOkFil.txt")
        val altOkParser = FileParser(altOkFil)

        test("Alle linjer skal være av type KravLinje") {
            altOkParser.parseKravLinjer().size shouldBe 101
        }

        test("startlinje skal være av type KontrollLinjeHeader") {
            altOkParser.parseKontrollLinjeHeader() shouldBe
                KontrollLinjeHeader(
                    transaksjonsDato = "20230526221340",
                    avsender = "OB04",
                )
        }

        test("sluttLinje skal være av type KontrollLinjeFooter") {
            altOkParser.parseKontrollLinjeFooter() shouldBe
                KontrollLinjeFooter(
                    transaksjonTimestamp = "20230526221340",
                    avsender = "OB04",
                    antallTransaksjoner = 101,
                    sumAlleTransaksjoner = "2645917.40".toBigDecimal(),
                )
        }

        test("Ugyldig BigDecimal skal kaste ParseException") {
            shouldThrow<ParseException> {
                FileParser(
                    getFileContent("FeilIParsingBigDecimal.txt"),
                ).parseKravLinjer()
            }
        }

        test("Ugyldig Int skal kaste ParseException") {
            shouldThrow<ParseException> {
                FileParser(
                    getFileContent("FeiliParsingAvInt.txt"),
                ).parseKontrollLinjeFooter()
            }
        }

        test("Feil encoded Ø skal erstattes med Ø") {
            val kravMedFeilEncoding = getFileContent("FA_FO_Feilencoding.txt")
            FileParser(kravMedFeilEncoding).parseKravLinjer().filter { linje -> linje.kravKode == "FA FØ" }.size shouldBe 1
        }

        test("Hvis linje ikke har fremtidig ytelse skal den settes til 0") {
            val utenFremtidigYtelse = getFileContent("Fil-uten-fremtidigytelse.txt")
            FileParser(utenFremtidigYtelse).parseKravLinjer().run {
                first { it.saksnummerNav == "FinnesIkke" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer1" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer2" }.fremtidigYtelse shouldBe BigDecimal.ZERO

                count { it.fremtidigYtelse == BigDecimal.ZERO } shouldBe 3
                count { it.fremtidigYtelse != BigDecimal.ZERO } shouldBe 2
            }
        }
    })
