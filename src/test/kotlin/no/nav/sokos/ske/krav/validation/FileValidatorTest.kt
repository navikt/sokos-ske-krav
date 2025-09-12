package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.util.TestUtilFunctions.getFileContent

internal class FileValidatorTest :
    BehaviorSpec({
        val controlLines = 2

        Given("Fil er OK") {
            val fileName = "AltOkFil.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = FileValidator.validateFile(content)

                Then("Skal ValidationResult være Success") {
                    (validationResult is ValidationResult.Success) shouldBe true
                }
                And("Alle kravlinjer skal returneres") {
                    (validationResult as ValidationResult.Success).kravLinjer.size shouldBe content.size - controlLines
                }
            }
        }

        Given("En fil har feil antall linjer i kontroll-linjen") {
            val fileName = "FilMedFeilAntallKrav.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = FileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe FEIL_I_ANTALL
                    }
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val fileName = "FilMedFeilSum.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = FileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe FEIL_I_SUM
                    }
                }
            }
        }

        Given("En fil har forskjellige datoer i kontroll-linjene") {
            val fileName = "FilMedFeilUtbetalDato.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = FileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe FEIL_I_DATO
                    }
                }
            }
        }

        Given("En fil har alle typer feil") {
            val fileName = "FilMedAlleTyperFeilForFilValidering.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = FileValidator.validateFile(content)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 3
                        count { it.first == FEIL_I_DATO } shouldBe 1
                        count { it.first == FEIL_I_SUM } shouldBe 1
                        count { it.first == FEIL_I_ANTALL } shouldBe 1
                    }
                }
            }
        }

        Given("En fil har feil i parsing av BigDecimal") {
            val fileName = "FeilIParsingBigDecimal.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                Then("Skal Exception kastes og melding skal inneholde 'Feil i parsing av BigDecimal'") {
                    val validationResult = FileValidator.validateFile(content)
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        count { it.first == PARSE_EXCEPTION } shouldBe 1
                        count { it.first == PARSE_EXCEPTION && it.second.contains("Feil i parsing av BigDecimal") } shouldBe 1
                    }
                }
            }
        }

        Given("En fil har feil i parsing av Int") {
            val fileName = "FeiliParsingAvInt.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                Then("Skal Exception kastes og melding skal inneholde 'Feil i parsing av Int'") {
                    val validationResult = FileValidator.validateFile(content)
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        count { it.first == PARSE_EXCEPTION } shouldBe 1
                        count { it.first == PARSE_EXCEPTION && it.second.contains("Feil i parsing av Int") } shouldBe 1
                    }
                }
            }
        }
    })
