package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent
import no.nav.sokos.ske.krav.util.shouldBe

internal class FileValidatorTest :
    BehaviorSpec({
        val controlLines = 2
        val fileValidator = FileValidator()

        Given("Fil er OK") {
            val fileName = "AllValideringOk.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)

                Then("Skal ValidationResult være Success") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
                And("Alle kravlinjer skal returneres") {
                    (validationResult as ValidationResult.Success).kravLinjer.size shouldBe content.size - controlLines
                }
            }
        }

        Given("En fil har feil antall linjer i kontroll-linjen") {
            val fileName = "validering/filvalidering/FeilAntallKrav.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        size shouldBe 1
                        first().header shouldBe ErrorKeys.FEIL_I_ANTALL
                    }
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val fileName = "validering/filvalidering/FeilSum.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        size shouldBe 1
                        first().header shouldBe ErrorKeys.FEIL_I_SUM
                    }
                }
            }
        }

        Given("En fil har forskjellige datoer i kontroll-linjene") {
            val fileName = "validering/filvalidering/FeilUtbetalDato.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        size shouldBe 1
                        first().header shouldBe ErrorKeys.FEIL_I_DATO
                    }
                }
            }
        }

        Given("En fil har alle typer feil") {
            val fileName = "validering/filvalidering/AlleTyperFeil.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(4)
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_DATO }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_SUM }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_ANTALL }
                        forOne { it.header shouldBe ErrorKeys.FAGSYSTEMID_MANGLER }
                    }
                }
            }
        }

        Given("En fil har feil i parsing av BigDecimal") {
            val fileName = "validering/filvalidering/FeilParsingBigDecimal.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                Then("Skal parsingfeil registreres og melding skal inneholde 'Feil i parsing av BigDecimal'") {
                    val validationResult = fileValidator.validateFile(content)
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(5)
                        forAll { (header, description, _) ->
                            header shouldBe ErrorKeys.PARSE_EXCEPTION
                            description shouldContain "Feil i parsing av BigDecimal"
                        }
                    }
                }
            }
        }

        Given("En fil har feil i parsing av Int") {
            val fileName = "validering/filvalidering/FeilParsingInt.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                Then("Skal Exception kastes og melding skal inneholde 'Feil i parsing av Int'") {
                    val validationResult = fileValidator.validateFile(content)
                    with((validationResult as ValidationResult.Error).errors) {
                        shouldHaveSize(1)
                        first().should {
                            it.header shouldBe ErrorKeys.PARSE_EXCEPTION
                            it.description shouldContain "Feil i parsing av Int"
                        }
                    }
                }
            }
        }

        Given("En fil med null-sum i kontroll-linjen og ingen kravlinjer") {
            val fileName = "validering/filvalidering/NullSumOk.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content)

                Then("Skal ValidationResult være Success - BigDecimal scale-forskjell skal ikke gi FEIL_I_SUM ved null-sum") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
            }
        }
    })
