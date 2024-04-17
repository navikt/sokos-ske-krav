package sokos.ske.krav.validation


import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import java.math.BigDecimal
import java.time.LocalDate

internal class LineValidationTest : FunSpec({

    test("Validering av linje skal returnere true når validering er ok") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lines  = LineValidator().validateNewLines(fil, dsMock)
        lines.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 1
    }

    test("Validering av linje skal feile når kravtypen er ugyldig") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "TA", "arsak",
            BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateKravtype"](kravLinje) as Boolean
            res shouldBe false
        }
    }

    test("Beløp kan ikke være 0 når det er nytt krav eller krav som skal endres") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateSaksnr"]("refgammelsak") as Boolean
            res shouldBe false
        }
    }

    test("Saksnummer må være riktig formatert") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }

        val kravLinje = KravLinje(
            1, "saksnummer_ø", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateSaksnr"](any<String>()) as Boolean
            res shouldBe false
        }
    }

    test("Refnummer gamme sak må være riktig formatert") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak_ø",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateSaksnr"](any<String>()) as Boolean
            res shouldBe false
        }
    }

    test("Vedtaksdato kan ikke være i fremtiden") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now().plusDays(1), "gjelderID",
            "20231201", "20231212", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateVedtaksdato"](any<LocalDate>()) as Boolean
            res shouldBe false
        }
    }

    test("Periode må være i fortid og fom må være før tom") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20241212", "20241201", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validatePeriode"](any<String>(), any<String>()) as Boolean
            res shouldBe false
        }
    }

    test("utbetalingsdato må være i fortid") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20231201", "20231230", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now(), "1234"
        )
        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validateUtbetalingsDato"](any<LocalDate>(), any<LocalDate>()) as Boolean
            res shouldBe false
        }

    }
    test("validering av periode skal returnere false når dato er på ugyldig format") {
        val dsMock = mockk<DatabaseService>() {
            justRun { saveValidationError(any(), any(), any())}
        }
        val kravLinje = KravLinje(
            1, "saksnummer", BigDecimal.ONE, LocalDate.now(), "gjelderID",
            "20233030", "20231202", "KS KS", "refgammelsak",
            "20230112", "bosted", "beh", "T", "arsak",
            BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().minusDays(1), "1234"
        )

        val fil = FtpFil(
            this.testCase.name.testName,
            emptyList(),
            kravLinjer = listOf(kravLinje)
        )

        val lineVal = spyk<LineValidator>(recordPrivateCalls = true)
        lineVal.validateNewLines(fil, dsMock)

        verify {
            val res: Boolean = lineVal["validatePeriode"](any<String>(), any<String>()) as Boolean
            res shouldBe false
        }
    }

})