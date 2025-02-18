package sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KontrollLinjeFooter
import sokos.ske.krav.domain.nav.KontrollLinjeHeader
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import sokos.ske.krav.validation.LineValidationRules
import java.io.File
import java.math.BigDecimal

internal class FileParserTest :
    FunSpec({
        val altOkFil = fileAsList("${File.separator}FtpFiler${File.separator}AltOkFil.txt")
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

        test("Ugyldig dato skal erstattes med errordate") {
            val feilIDatoKrav = fileAsList("${File.separator}FtpFiler${File.separator}FilMedFeilUtbetalDato.txt")
            FileParser(feilIDatoKrav)
                .parseKravLinjer()
                .filter { linje ->
                    linje.utbetalDato == LineValidationRules.errorDate
                }.run {
                    get(0).linjenummer shouldBe 7
                    get(1).linjenummer shouldBe 9
                }
        }

        test("Feil encoded Ø skal erstattes med Ø") {
            val kravMedFeilEncoding = fileAsList("${File.separator}FtpFiler${File.separator}FA_FO_Feilencoding.txt")
            FileParser(kravMedFeilEncoding).parseKravLinjer().filter { linje -> linje.kravKode == "FA FØ" }.size shouldBe 1
        }

        test("Hvis linje ikke har fremtidig ytelse skal den settes til 0") {
            val utenFremtidigYtelse = fileAsList("${File.separator}FtpFiler${File.separator}Fil-uten-fremtidigytelse.txt")
            FileParser(utenFremtidigYtelse).parseKravLinjer().run {
                first { it.saksnummerNav == "FinnesIkke" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer1" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer2" }.fremtidigYtelse shouldBe BigDecimal.ZERO

                count { it.fremtidigYtelse == BigDecimal.ZERO } shouldBe 3
                count { it.fremtidigYtelse != BigDecimal.ZERO } shouldBe 2
            }
        }
    })
