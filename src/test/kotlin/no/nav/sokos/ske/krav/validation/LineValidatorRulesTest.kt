package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.shouldContain

internal class LineValidatorRulesTest :
    BehaviorSpec({
        val lineValidator = LineValidator()
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
                utbetalDato = LocalDate.now().minusDays(5),
                fagsystemId = "1234",
                tilleggsfrist = LocalDate.now().minusMonths(1),
                avsender = "OB04",
            )

        Given("Vedtaksdato skal valideres") {
            When("Vedtaksdato er i fortid") {
                val krav = okLinje.copy(vedtaksDato = LocalDate.now().minusDays(1))
                val validationResult = lineValidator.validate(krav)

                Then("Skal ValidationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }

            When("Vedtaksdato er i dag") {
                val krav = okLinje.copy(vedtaksDato = LocalDate.now())
                val validationResult = lineValidator.validate(krav)

                Then("Skal ValidationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }
            When("Vedtaksdato er i fremtid") {
                val krav = okLinje.copy(vedtaksDato = LocalDate.now().plusDays(1))
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            it.description shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }
            When("Vedtaksdato er feil formattert i fil") {
                val krav = okLinje.copy(vedtaksDato = LineValidator.errorDate)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            it.description shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }
        }

        Given("Utbetalingsdato skal valideres") {
            When("Utbetalingsdato er før vedtaksdato") {
                val vedtaksdato = LocalDate.now()
                val krav = okLinje.copy(utbetalDato = vedtaksdato.minusDays(1), vedtaksDato = vedtaksdato)
                val validationResult = lineValidator.validate(krav)

                Then("Skal ValidationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }

            When("Utbetalingsdato er lik vedtaksdato") {
                val vedtaksdato = LocalDate.now()
                val krav = okLinje.copy(utbetalDato = vedtaksdato, vedtaksDato = vedtaksdato)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.description shouldContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }

            When("Utbetalingsdato er etter vedtaksdato") {
                val vedtaksdato = LocalDate.now()
                val krav = okLinje.copy(utbetalDato = vedtaksdato.plusDays(1), vedtaksDato = vedtaksdato)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.description shouldContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }

            When("Utbetalingsdato er ikke oppgitt (error date) for OB04 skal gi feil") {
                val krav = okLinje.copy(utbetalDato = LineValidator.errorDate, avsender = Avsender.OB04.name)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.description shouldContain ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }

            When("Utbetalingsdato er ikke oppgitt (error date) for Arena skal være ok") {
                val krav = okLinje.copy(utbetalDato = LineValidator.errorDate, avsender = Avsender.ARENA.name)
                val validationResult = lineValidator.validate(krav)

                Then("Skal ValidationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }
                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }
        }

        Given("Tilleggsfristdato skal valideres") {
            When("Tilleggsfristdato er ikke eldre enn 10 måneder") {
                val tilleggsfristDato = LocalDate.now().minusMonths(5)
                val krav = okLinje.copy(tilleggsfrist = tilleggsfristDato)
                val validationResult = lineValidator.validate(krav)

                Then("Skal ValidationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }
            When("Tilleggsfristdato er eldre enn 10 måneder") {
                val tilleggsfristDato = LocalDate.now().minusMonths(11)
                val krav = okLinje.copy(tilleggsfrist = tilleggsfristDato)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.TILLEGGSFRISTDATO_ERROR
                            it.description shouldContain ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD
                            it.description shouldContain tilleggsfristDato.toString()
                            it.description shouldContain krav.linjenummer.toString()
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }
        }

        Given("Periode skal valideres") {
            When("PeriodeTOM er neste måned") {
                val periodeTom =
                    LocalDate
                        .now()
                        .plusMonths(1)
                        .toString()
                        .replace("-", "")

                val periodeFom =
                    LocalDate
                        .now()
                        .minusDays(1)
                        .toString()
                        .replace("-", "")

                val krav = okLinje.copy(periodeFOM = periodeFom, periodeTOM = periodeTom)
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.PERIODE_ERROR
                            it.description shouldContain ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }

            When("PeriodeFOM er før periodeTOM") {
                val krav = okLinje.copy(periodeFOM = "20241209", periodeTOM = "20241210")
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }

            When("PeriodeFOM er lik periodeTOM") {
                val krav = okLinje.copy(periodeFOM = "20241210", periodeTOM = "20241210")
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være success") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Success>()
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValid()
                    }
                }
            }

            When("PeriodeFOM er etter periodeTOM") {
                val krav = okLinje.copy(periodeFOM = "20241211", periodeTOM = "20241210")
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.PERIODE_ERROR
                            it.description shouldContain ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }

            When("PeriodeFOM er feil formattert i fil") {
                val krav = okLinje.copy(periodeFOM = LineValidator.errorDate.toString())
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.PERIODE_ERROR
                            it.description shouldContain ErrorMessages.PERIODE_FOM_WRONG_FORMAT
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }
            When("PeriodeTOM er feil formattert i fil") {
                val krav = okLinje.copy(periodeTOM = LineValidator.errorDate.toString())
                val validationResult = lineValidator.validate(krav)

                Then("Skal validationResult være error") {
                    validationResult.shouldBeInstanceOf<ValidationResult.Error>()
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.PERIODE_ERROR
                            it.description shouldContain ErrorMessages.PERIODE_TOM_WRONG_FORMAT
                            it.caseNumber.shouldNotBeNull()
                        }
                    }
                }

                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Error).originalLines) {
                        shouldNotBeNull()
                        shouldHaveSize(1)
                        first() shouldBe krav.markedAsValidationError()
                    }
                }
            }
        }

        Given("Et krav har ugyldig kravtype") {
            val krav = okLinje.copy(kravKode = "MJ AU", kodeHjemmel = "VO FF")
            val validationResult = lineValidator.validate(krav)

            Then("Skal validationResult være error") {
                validationResult.shouldBeInstanceOf<ValidationResult.Error>()
            }

            And("Feilmelding skal returneres") {
                with((validationResult as ValidationResult.Error).errors) {
                    shouldHaveSize(1)
                    first().should {
                        it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                        it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            And("Linje skal returneres") {
                with((validationResult as ValidationResult.Error).originalLines) {
                    shouldNotBeNull()
                    shouldHaveSize(1)
                    first() shouldBe krav.markedAsValidationError()
                }
            }
        }

        Given("Et krav har saksnummer som er feil formattert i fil") {
            val krav = okLinje.copy(saksnummerNav = "saksnummer_ø")
            val validationResult = lineValidator.validate(krav)

            Then("Skal validationResult være error") {
                validationResult.shouldBeInstanceOf<ValidationResult.Error>()
            }

            And("Feilmelding skal returneres") {
                with((validationResult as ValidationResult.Error).errors) {
                    shouldHaveSize(1)
                    first().should {
                        it.header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                        it.description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            And("Linje skal returneres") {
                with((validationResult as ValidationResult.Error).originalLines) {
                    shouldNotBeNull()
                    shouldHaveSize(1)
                    first() shouldBe krav.markedAsValidationError()
                }
            }
        }

        Given("Et krav har referansenummerGammelSak som er feil formattert i fil") {
            val krav = okLinje.copy(referansenummerGammelSak = "refnrgammel_ø")
            val validationResult = lineValidator.validate(krav)

            Then("Skal validationResult være error") {
                validationResult.shouldBeInstanceOf<ValidationResult.Error>()
            }

            And("Feilmelding skal returneres") {
                with((validationResult as ValidationResult.Error).errors) {
                    shouldHaveSize(1)
                    first().should {
                        it.header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        it.description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            And("Linje skal returneres") {
                with((validationResult as ValidationResult.Error).originalLines) {
                    shouldNotBeNull()
                    shouldHaveSize(1)
                    first() shouldBe krav.markedAsValidationError()
                }
            }
        }

        Given("Et krav har blank gjelderId") {
            val krav = okLinje.copy(gjelderId = "   ")
            val validationResult = lineValidator.validate(krav)

            Then("Skal validationResult være error") {
                validationResult.shouldBeInstanceOf<ValidationResult.Error>()
            }

            And("Feilmelding skal returneres") {
                with((validationResult as ValidationResult.Error).errors) {
                    shouldHaveSize(1)
                    first().should {
                        it.header shouldBe ErrorKeys.GJELDERID_ERROR
                        it.description shouldContain ErrorMessages.GJELDERID_MISSING
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            And("Linje skal returneres") {
                with((validationResult as ValidationResult.Error).originalLines) {
                    shouldNotBeNull()
                    shouldHaveSize(1)
                    first() shouldBe krav.markedAsValidationError()
                }
            }
        }

        Given("Et krav har negativt beløp") {
            val krav = okLinje.copy(belop = BigDecimal("-100.00"))
            val validationResult = lineValidator.validate(krav)

            Then("Skal validationResult være error") {
                validationResult.shouldBeInstanceOf<ValidationResult.Error>()
            }

            And("Feilmelding skal returneres") {
                with((validationResult as ValidationResult.Error).errors) {
                    shouldHaveSize(1)
                    first().should {
                        it.header shouldBe ErrorKeys.HOVEDSTOL_ERROR
                        it.description shouldContain ErrorMessages.BELOP_NEGATIVE
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            And("Linje skal returneres") {
                with((validationResult as ValidationResult.Error).originalLines) {
                    shouldNotBeNull()
                    shouldHaveSize(1)
                    first() shouldBe krav.markedAsValidationError()
                }
            }
        }
    })
