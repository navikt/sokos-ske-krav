package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.justRun
import io.mockk.mockk
import io.kotest.matchers.types.shouldBeInstanceOf
import kotliquery.TransactionalSession
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.service.FtpFil
import no.nav.sokos.ske.krav.validation.LineValidationRules.errorDate
import no.nav.sokos.ske.krav.validation.LineValidationRules.runValidation

internal class LineValidatorTest :
    BehaviorSpec({
        val filValideringsfeilRepository =
            mockk<FilValideringsfeilRepository> {
                justRun { insertLineFilValideringsfeil(any<TransactionalSession>(), any<String>(), any<KravLinje>(), any<String>()) }
            }

        fun ftpFile(
            name: String,
            kravLinjer: List<KravLinje>,
        ) = FtpFil(name, kravLinjer)

        Given("Alle linjer er ok") {
            val kravLinjer = getKravlinjer()
            val fileName = this.testCase.name.name

            When("Linjer valideres") {
                val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

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
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

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
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))
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
                val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

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
                    val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))
                    val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

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

        Given("OB04 linje mangler fagsystemId") {
            val okKrav = getKravlinjer()
            val ikkeOkKrav = listOf(okKrav[0].copy(linjenummer = 6, fagsystemId = ""))
            val kravLinjer = okKrav + ikkeOkKrav
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }

                And("Validering skal returnere 1 feil-linje") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 1
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }
        }

        Given("Arena linje med blank fagsystemId") {
            val arenaLinje = getKravlinjer().first().copy(avsender = Avsender.ARENA.name, fagsystemId = "", utbetalDato = errorDate)
            val kravLinjer = listOf(arenaLinje)
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

                Then("Blank fagsystemId skal ikke gi feil for Arena") {
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }
            }
        }

        Given("Pesys linje med blank fagsystemId") {
            val pesysLinje = getKravlinjer().first().copy(avsender = Avsender.PESYS.name, fagsystemId = "", utbetalDato = errorDate)
            val kravLinjer = listOf(pesysLinje)
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

                Then("Blank fagsystemId skal ikke gi feil for Pesys") {
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }
            }
        }

        Given("Infotrygd linje med blank fagsystemId") {
            val infotrygdLinje = getKravlinjer().first().copy(avsender = Avsender.INFOTRYGD.name, fagsystemId = "", utbetalDato = errorDate)
            val kravLinjer = listOf(infotrygdLinje)
            val fileName = this.testCase.name.name
            val lineValidator = LineValidator(dataSource, filValideringsfeilRepository, SlackService(mockk<SlackClient>(relaxed = true)))

            When("Linjer valideres") {
                val validatedLines = lineValidator.validateNewLines(ftpFile(fileName, kravLinjer))

                Then("Blank fagsystemId skal ikke gi feil for Infotrygd") {
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }
            }
        }

        Given("nå skal jeg så seks forskjellige scenarier av referansenummergammelsak vs ") {
            When("kravlinje er stopp og referansenummergammelsak er tom") {
                val result = runValidation(stoppLinje.copy(referansenummerGammelSak = ""))
Then("valideringen skal varsle at referansen mangler og tagge produktleder") {
    val error = result.shouldBeInstanceOf<ValidationResult.Error>()
    error.messages.size shouldBe 1
    error.messages.first().second shouldBe "ReferanseNummerGammelSak mangler for stopp i fil. Linje: 1"
}
            }
            When("kravlinje er endring og referansenummergammelsak er tom") {
                val result = runValidation(endringsLinje(" "))
                Then("valideringen skal varsle at referansen mangler og tagge produktleder") {
                    assertInstanceOf<ValidationResult.Error>(result)
                    result.messages.size shouldBe 1
                    result.messages.first().second shouldBe "ReferanseNummerGammelSak mangler for endring i fil. Linje: 1"
                }
            }
            When("kravlinje er opprett og referansenummergammelsak er tom") {
                val result = runValidation(okLinje.copy(referansenummerGammelSak = ""))
Then("valideringen er ok") {
    result.shouldBeInstanceOf<ValidationResult.Success>()
}
            }

            When("kravlinje er stopp og referansenummergammelsak ikke er tom, men er invalid") {
                val result = runValidation(stoppLinje.copy(referansenummerGammelSak = "!invalid?"))
                Then("valideringen skal varsle at formatet er feil") {
                    assertInstanceOf<ValidationResult.Error>(result)
                    result.messages.size shouldBe 1
                    result.messages.first().second shouldBe "ReferanseNummerGammelSak er feil formattert i fil: (!invalid?). Linje: 1"
                }
            }
            When("kravlinje er endring og referansenummergammelsak ikke er tom, men er invalid") {
                val result = runValidation(endringsLinje("!invalid?"))
                Then("valideringen skal varsle at formatet er feil") {
                    assertInstanceOf<ValidationResult.Error>(result)
                    result.messages.size shouldBe 1
                    result.messages.first().second shouldBe "ReferanseNummerGammelSak er feil formattert i fil: (!invalid?). Linje: 1"
                }
            }
            When("kravlinje er opprett og referansenummergammelsak ikke er tom, men er invalid") {
                val result = runValidation(okLinje.copy(referansenummerGammelSak = "!invalid?"))
                Then("valideringen skal varsle at formatet er feil") {
                    assertInstanceOf<ValidationResult.Error>(result)
                    result.messages.size shouldBe 1
                    result.messages.first().second shouldBe "ReferanseNummerGammelSak er feil formattert i fil: (!invalid?). Linje: 1"
                }
            }
        }
    })

private val okLinje =
    KravLinje(
        linjenummer = 1,
        saksnummerNav = "saksnummer",
        belop = BigDecimal.ONE,
        vedtaksDato = LocalDate.now(),
        gjelderId = "gjelderID",
        periodeFOM = "20231201",
        periodeTOM = "20231212",
        kravKode = "KS KS",
        referansenummerGammelSak = "",
        transaksjonsDato = "20230112",
        enhetBosted = "bosted",
        enhetBehandlende = "beh",
        kodeHjemmel = "T",
        kodeArsak = "arsak",
        belopRente = BigDecimal.ONE,
        fremtidigYtelse = BigDecimal.ONE,
        utbetalDato = LocalDate.now().minusDays(1),
        fagsystemId = "1234",
        avsender = Avsender.OB04.name,
    )
private val stoppLinje = okLinje.copy(belop = BigDecimal.ZERO)

private fun endringsLinje(refnrGammelSak: String) = stoppLinje.copy(belop = BigDecimal.TWO, referansenummerGammelSak = refnrGammelSak)

private fun getKravlinjer(): MutableList<KravLinje> =
    mutableListOf(
        okLinje,
        okLinje.copy(linjenummer = 2, saksnummerNav = "saksnummer2"),
        okLinje.copy(linjenummer = 3, saksnummerNav = "saksnummer3"),
        okLinje.copy(linjenummer = 4, saksnummerNav = "saksnummer4"),
        okLinje.copy(linjenummer = 5, saksnummerNav = "saksnummer5"),
    )
