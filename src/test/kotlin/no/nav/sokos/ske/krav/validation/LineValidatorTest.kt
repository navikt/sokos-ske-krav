package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.FtpFil
import no.nav.sokos.ske.krav.validation.LineValidationRules.errorDate

internal class LineValidatorTest :
    BehaviorSpec({
        val dbService = mockk<DatabaseService>(relaxed = true)

        fun ftpFile(
            name: String,
            kravLinjer: List<KravLinje>,
        ) = FtpFil(name, emptyList(), kravLinjer)

        Given("Alle linjer er ok") {
            val kravLinjer = getKravlinjer()
            val fileName = this.testCase.name.name

            When("Linjer valideres") {
                val lineValidator = LineValidator(SlackService(mockk<SlackClient>(relaxed = true)))
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer), dbService)

                Then("Skal validering returnere ${kravLinjer.size} ok kravlinjer") {
                    val updatedLines = kravLinjer.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe kravLinjer.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }

                And("Ingen feil linjer") {
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }
            }
        }

        Given("1 linje har har 1 feil") {
            val okKrav = getKravlinjer()
            val ikkeOkKrav = listOf(okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU"))

            val kravLinjer = okKrav + ikkeOkKrav
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer), dbService)

                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }

                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            val okKrav = getKravlinjer()
            val ikkeOkKrav =
                listOf(
                    okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU", vedtaksDato = LocalDate.now().plusMonths(1), saksnummerNav = "saksnummer_ø"),
                )

            val kravLinjer = okKrav + ikkeOkKrav
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer), dbService)
                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }
                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val okKrav = getKravlinjer()
            val ikkeOkKrav =
                listOf(
                    okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 7, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 8, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 9, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 10, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 11, kravKode = "MJ AU"),
                )

            When("Linjer valideres") {
                val kravLinjer = okKrav + ikkeOkKrav
                val fileName = this.testCase.name.name
                val lineValidator = LineValidator(SlackService(mockk<SlackClient>(relaxed = true)))
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer), dbService)

                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }
                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }

            And("3 linjer har ulike feil") {
                val ikkeOkKravMedUlikeFeil =
                    listOf(
                        ikkeOkKrav[0],
                        ikkeOkKrav[1],
                        ikkeOkKrav[2],
                        ikkeOkKrav[3].copy(saksnummerNav = "saksnummer_ø"),
                        ikkeOkKrav[4].copy(referansenummerGammelSak = "refgammel_ø"),
                        ikkeOkKrav[5].copy(vedtaksDato = errorDate),
                    )

                When("Linjer valideres") {
                    val kravLinjer = okKrav + ikkeOkKravMedUlikeFeil
                    val fileName = this.testCase.name.name
                    val lineValidator = LineValidator(SlackService(mockk<SlackClient>(relaxed = true)))
                    val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer), dbService)

                    Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                        val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                        val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                        (updatedLines + validated).toSet().size shouldBe okKrav.size
                        updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                    }
                    And("Validering skal returnere ${ikkeOkKravMedUlikeFeil.size} feil-linjer") {
                        with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                            size shouldBe ikkeOkKrav.size
                            first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                        }
                    }
                }
            }
        }
    })

private fun getKravlinjer(): MutableList<KravLinje> {
    val okLinje =
        KravLinje(
            linjenummer = 1,
            saksnummerNav = "saksnummer",
            belop = BigDecimal.ONE,
            vedtaksDato = LocalDate.now(),
            gjelderId = "gjelderID",
            periodeFOM = "20231201",
            periodeTOM = "20231212",
            kravKode = "KS KS",
            referansenummerGammelSak = "refgammelsak",
            transaksjonsDato = "20230112",
            enhetBosted = "bosted",
            enhetBehandlende = "beh",
            kodeHjemmel = "T",
            kodeArsak = "arsak",
            belopRente = BigDecimal.ONE,
            fremtidigYtelse = BigDecimal.ONE,
            utbetalDato = LocalDate.now().minusDays(1),
            fagsystemId = "1234",
            avsender = "OB04",
        )
    return mutableListOf(
        okLinje,
        okLinje.copy(linjenummer = 2, saksnummerNav = "saksnummer2"),
        okLinje.copy(linjenummer = 3, saksnummerNav = "saksnummer3"),
        okLinje.copy(linjenummer = 4, saksnummerNav = "saksnummer4"),
        okLinje.copy(linjenummer = 5, saksnummerNav = "saksnummer5"),
    )
}
