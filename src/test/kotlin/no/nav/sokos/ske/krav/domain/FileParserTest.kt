package no.nav.sokos.ske.krav.domain

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parsing.ParseException

import no.nav.sokos.ske.krav.domain.nav.FileParser
import no.nav.sokos.ske.krav.domain.nav.KontrollLinjeFooter
import no.nav.sokos.ske.krav.domain.nav.KontrollLinjeHeader
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

        test("Hvis tilleggsfrist ikke finnes skal vi få null på feltet") {
            val kravLinjer = altOkParser.parseKravLinjer()
            val linjerUtenTilleggsfrist = kravLinjer.filter { it.tilleggsfristEtterForeldelsesloven == null }

            linjerUtenTilleggsfrist.size shouldBe 95
        }

        test("Hvis tilleggsfrist finnes, skal vi få riktig dato utledet fra feltet") {
            val kravLinjer = altOkParser.parseKravLinjer()
            val linjerMedTilleggsfrist = kravLinjer.filter { it.tilleggsfristEtterForeldelsesloven != null }

            linjerMedTilleggsfrist.size shouldBe 6

            linjerMedTilleggsfrist[0].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2024, 12, 31)
            linjerMedTilleggsfrist[1].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2025, 6, 30)
            linjerMedTilleggsfrist[2].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2025, 4, 15)
            linjerMedTilleggsfrist[3].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2025, 2, 28)
            linjerMedTilleggsfrist[4].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2024, 12, 8)
            linjerMedTilleggsfrist[5].tilleggsfristEtterForeldelsesloven shouldBe LocalDate.of(2025, 2, 10)
        }
    })
