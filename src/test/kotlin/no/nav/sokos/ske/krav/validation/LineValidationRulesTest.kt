package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import no.nav.sokos.ske.krav.domain.nav.KravLinje

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
                tilleggsfristEtterForeldelsesloven = LocalDate.now().minusMonths(1),
            )

        Given("Et krav har ingen feil") {
            When("Krav valideres") {
                val validationResult: ValidationResult = LineValidationRules.runValidation(okLinje)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        size shouldBe 1
                        first() shouldBe okLinje
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
                        size shouldBe 1
                        first() shouldBe krav
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
                        size shouldBe 1
                        first() shouldBe krav
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.VEDTAKSDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.VEDTAKSDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
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
                        size shouldBe 1
                        first() shouldBe krav
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.UTBETALINGSDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.UTBETALINGSDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
                    }
                }
            }
            When("Utbetalingsdato er feil formattert i fil") {
                val krav = okLinje.copy(utbetalDato = LineValidationRules.errorDate)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.UTBETALINGSDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
                    }
                }
            }
        }

        Given("Tilleggsfristdato skal valideres") {
            When("Tilleggsfristdato er ikke eldre enn 10 måneder") {
                val tilleggsfristDato = LocalDate.now().minusMonths(5)
                val krav = okLinje.copy(tilleggsfristEtterForeldelsesloven = tilleggsfristDato)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)
                Then("Skal ValidationResult være success") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
                And("Linje skal returneres") {
                    with((validationResult as ValidationResult.Success).kravLinjer) {
                        size shouldBe 1
                        first() shouldBe krav
                    }
                }
            }
            When("Tilleggsfristdato er eldre enn 10 måneder") {
                val tilleggsfristDato = LocalDate.now().minusMonths(11)
                val krav = okLinje.copy(tilleggsfristEtterForeldelsesloven = tilleggsfristDato)
                val validationResult: ValidationResult = LineValidationRules.runValidation(krav)

                Then("Skal validationResult være error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }

                And("Feilmelding skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.TILLEGGSFRISTDATO_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD
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
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.PERIODE_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
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
                        size shouldBe 1
                        first() shouldBe krav
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
                        size shouldBe 1
                        first() shouldBe krav
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.PERIODE_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.PERIODE_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.PERIODE_FOM_WRONG_FORMAT
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.PERIODE_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.PERIODE_TOM_WRONG_FORMAT
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.KRAVTYPE_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.SAKSNUMMER_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.SAKSNUMMER_WRONG_FORMAT
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
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe LineValidationRules.ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        first().second shouldContain LineValidationRules.ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                    }
                }
            }
        }
    })
