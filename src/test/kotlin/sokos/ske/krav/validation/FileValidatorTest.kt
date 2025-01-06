package sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import sokos.ske.krav.validation.FileValidator.ErrorKeys
import java.io.File

internal class FileValidatorTest :
    BehaviorSpec({
        val fileValidator = FileValidator(mockk<SlackClient>(relaxed = true))
        val controlLines = 2

        Given("Fil er OK") {
            val fileName = "AltOkFil.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content, fileName)

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
                val validationResult = fileValidator.validateFile(content, fileName)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe ErrorKeys.FEIL_I_ANTALL
                    }
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val fileName = "FilMedFeilSum.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content, fileName)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe ErrorKeys.FEIL_I_SUM
                    }
                }
            }
        }

        Given("En fil har forskjellige datoer i kontroll-linjene") {
            val fileName = "FilMedFeilSendtDato.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content, fileName)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 1
                        first().first shouldBe ErrorKeys.FEIL_I_DATO
                    }
                }
            }
        }

        Given("En fil har alle typer feil") {
            val fileName = "FilMedAlleTyperFeilForFilValidering.txt"
            val content = getFileContent(fileName)

            When("Filen valideres") {
                val validationResult = fileValidator.validateFile(content, fileName)
                Then("Skal ValidationResult være Error") {
                    (validationResult is ValidationResult.Error) shouldBe true
                }
                And("Feilmeldingen skal returneres") {
                    with((validationResult as ValidationResult.Error).messages) {
                        size shouldBe 3
                        count { it.first == ErrorKeys.FEIL_I_DATO } shouldBe 1
                        count { it.first == ErrorKeys.FEIL_I_SUM } shouldBe 1
                        count { it.first == ErrorKeys.FEIL_I_ANTALL } shouldBe 1
                    }
                }
            }
        }
    })

private fun getFileContent(filename: String) = fileAsList("${File.separator}FtpFiler${File.separator}/$filename")
