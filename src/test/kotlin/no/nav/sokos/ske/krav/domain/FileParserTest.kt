package no.nav.sokos.ske.krav.domain

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.KontrollLinjeFooter
import no.nav.sokos.ske.krav.copybook.KontrollLinjeHeader
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class FileParserTest :
    FunSpec({
        val altOkFil = getFileContent("innsender/OppdragFil.txt")
        val altOkResult = FileParser(altOkFil).parseResult.shouldBeInstanceOf<ParseResult.Success>()

        test("Alle linjer skal være av type KravLinje") {
            altOkResult.kravLinjer.size shouldBe 101
            altOkResult.kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.OB04.name
            }
        }

        test("startlinje skal være av type KontrollLinjeHeader") {
            altOkResult.kontrollLinjeHeader shouldBe
                KontrollLinjeHeader(
                    transaksjonsDato = "20230526221340",
                    avsender = Avsender.OB04.name,
                )
        }

        test("sluttLinje skal være av type KontrollLinjeFooter") {
            altOkResult.kontrollLinjeFooter shouldBe
                KontrollLinjeFooter(
                    transaksjonTimestamp = "20230526221340",
                    avsender = Avsender.OB04.name,
                    antallTransaksjoner = 101,
                    sumAlleTransaksjoner = "2645917.40".toBigDecimal(),
                )
        }

        test("Ugyldig BigDecimal skal gi en ErrorLinje") {
            val result = FileParser(getFileContent("validering/filvalidering/FeilParsingBigDecimal.txt")).parseResult
            result.shouldBeInstanceOf<ParseResult.Error>()
            result.messages.size shouldBe 5
        }

        test("Ugyldig Int i footeren skal gi feil i kontrollLinje") {
            FileParser(
                getFileContent("validering/filvalidering/FeilParsingInt.txt"),
            ).parseResult.shouldBeInstanceOf<ParseResult.Error>()
        }

        test("Feil encoded Ø skal erstattes med Ø") {
            val kravMedFeilEncoding = getFileContent("validering/filvalidering/FeilEncoding.txt")
            val result = FileParser(kravMedFeilEncoding).parseResult.shouldBeInstanceOf<ParseResult.Success>()
            result.kravLinjer.filter { it.kravKode == "FA FØ" }.size shouldBe 1
        }

        test("Hvis linje ikke har fremtidig ytelse skal den settes til 0") {
            val utenFremtidigYtelse = getFileContent("krav/UtenFremtidigYtelse.txt")
            val result = FileParser(utenFremtidigYtelse).parseResult.shouldBeInstanceOf<ParseResult.Success>()
            result.kravLinjer.run {
                first { it.saksnummerNav == "FinnesIkke" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer1" }.fremtidigYtelse shouldBe BigDecimal.ZERO
                first { it.saksnummerNav == "Dnummer2" }.fremtidigYtelse shouldBe BigDecimal.ZERO

                count { it.fremtidigYtelse == BigDecimal.ZERO } shouldBe 3
                count { it.fremtidigYtelse != BigDecimal.ZERO } shouldBe 2
            }
        }

        test("Hvis tilleggsfrist ikke finnes skal vi få null på feltet") {
            val linjerUtenTilleggsfrist = altOkResult.kravLinjer.filter { it.tilleggsfrist == null }
            linjerUtenTilleggsfrist.size shouldBe 95
        }

        test("Hvis tilleggsfrist finnes, skal vi få riktig dato utledet fra feltet") {
            val linjerMedTilleggsfrist = altOkResult.kravLinjer.filter { it.tilleggsfrist != null }

            linjerMedTilleggsfrist.size shouldBe 6

            linjerMedTilleggsfrist[0].tilleggsfrist shouldBe LocalDate.of(2040, 12, 31)
            linjerMedTilleggsfrist[1].tilleggsfrist shouldBe LocalDate.of(2040, 6, 30)
            linjerMedTilleggsfrist[2].tilleggsfrist shouldBe LocalDate.of(2040, 4, 15)
            linjerMedTilleggsfrist[3].tilleggsfrist shouldBe LocalDate.of(2040, 2, 28)
            linjerMedTilleggsfrist[4].tilleggsfrist shouldBe LocalDate.of(2040, 2, 8)
            linjerMedTilleggsfrist[5].tilleggsfrist shouldBe LocalDate.of(2040, 2, 10)
        }

        test("Tilleggsfrist håndteres korrekt i krav/MedTilleggsfrist.txt") {
            val result = FileParser(getFileContent("krav/MedTilleggsfrist.txt")).parseResult.shouldBeInstanceOf<ParseResult.Success>()
            result.kravLinjer.first().tilleggsfrist shouldBe LocalDate.of(2025, 3, 1)
        }
    })
