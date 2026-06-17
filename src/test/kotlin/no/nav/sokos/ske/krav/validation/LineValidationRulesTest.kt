package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender

internal class LineValidationRulesTest :
    BehaviorSpec({

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

        Given("Et krav har ingen feil") {
            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(okLinje)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        shouldHaveSize(1)
                        first() shouldBe okLinje.markedAsValid()
                    }
                }
            }
        }
        Given("Vedtaksdato skal valideres") {
            When("Vedtaksdato er i fortid") {
                val krav = okLinje.copy(vedtaksDato = LocalDate.now().minusDays(1))
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            it.second shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
            When("Vedtaksdato er feil formattert i fil") {
                val krav = okLinje.copy(vedtaksDato = LineValidationRules.errorDate)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            it.second shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }

        Given("Utbetalingsdato skal valideres") {
            When("Utbetalingsdato er før vedtaksdato") {
                val vedtaksdato = LocalDate.now()
                val krav = okLinje.copy(utbetalDato = vedtaksdato.minusDays(1), vedtaksDato = vedtaksdato)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.second shouldContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
            When("Utbetalingsdato er etter vedtaksdato") {
                val vedtaksdato = LocalDate.now()
                val krav = okLinje.copy(utbetalDato = vedtaksdato.plusDays(1), vedtaksDato = vedtaksdato)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.second shouldContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
            When("Utbetalingsdato er ikke oppgitt (error date) for OB04 skal gi feil") {
                val krav = okLinje.copy(utbetalDato = LineValidationRules.errorDate, avsender = Avsender.OB04.name)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.UTBETALINGSDATO_ERROR
                            it.second shouldContain ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
            When("Utbetalingsdato er ikke oppgitt (error date) for Arena skal være ok") {
                val krav = okLinje.copy(utbetalDato = LineValidationRules.errorDate, avsender = Avsender.ARENA.name)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.TILLEGGSFRISTDATO_ERROR
                            it.second shouldContain ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD.description
                            it.second shouldContain tilleggsfristDato.toString()
                            it.second shouldContain krav.saksnummerNav
                            it.second shouldContain krav.linjenummer.toString()
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        shouldHaveSize(1)
                        first().first shouldBe ErrorKeys.PERIODE_ERROR
                        first().second shouldContain ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE.description
                    }
                }
            }
            When("PeriodeFOM er før periodeTOM") {
                val krav = okLinje.copy(periodeFOM = "20241209", periodeTOM = "20241210")
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
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
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.PERIODE_ERROR
                            it.second shouldContain ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }

            When("PeriodeFOM er feil formattert i fil") {
                val krav = okLinje.copy(periodeFOM = LineValidationRules.errorDate.toString())
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.PERIODE_ERROR
                            it.second shouldContain ErrorMessages.PERIODE_FOM_WRONG_FORMAT.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
            When("PeriodeTOM er feil formattert i fil") {
                val krav = okLinje.copy(periodeTOM = LineValidationRules.errorDate.toString())
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.PERIODE_ERROR
                            it.second shouldContain ErrorMessages.PERIODE_TOM_WRONG_FORMAT.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }
        Given("Et krav har ugyldig kravtype") {
            val krav = okLinje.copy(kravKode = "MJ AU", kodeHjemmel = "VO FF")

            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.KRAVTYPE_ERROR
                            it.second shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }

        Given("Et krav har saksnummer som er feil formattert i fil") {
            val krav = okLinje.copy(saksnummerNav = "saksnummer_ø")

            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.SAKSNUMMER_ERROR
                            it.second shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT.description
                        }
                    }
                }
            }
        }
        Given("Et krav har referansenummerGammelSak som er feil formattert i fil") {
            val krav = okLinje.copy(referansenummerGammelSak = "refnrgammel_ø")

            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                            it.second shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }

        Given("Et krav har blank gjelderId") {
            val krav = okLinje.copy(gjelderId = "   ")

            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.GJELDERID_ERROR
                            it.second shouldContain ErrorMessages.GJELDERID_MISSING.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }

        Given("Et krav har negativt beløp") {
            val krav = okLinje.copy(belop = BigDecimal("-100.00"))

            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error)) {
                        messages.shouldHaveSize(1)
                        messages.first().should {
                            it.first shouldBe ErrorKeys.HOVEDSTOL_ERROR
                            it.second shouldContain ErrorMessages.BELOP_NEGATIVE.description
                        }

                        originalLines.should {
                            it.shouldNotBeNull()
                            it.shouldHaveSize(1)
                            it.first() shouldBe krav.markedAsValidationError()
                        }
                    }
                }
            }
        }
    })
