package sokos.ske.krav.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import java.io.File

internal class FileValidatorTest :
    FunSpec({

        test("Når validering av fil er OK skal ValidationResult.Success returneres med kravlinjene") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}/AltOkFil.txt")
            val validationResult =	FileValidator(mockk<SlackClient>(relaxed = true)).validateFile(liste, "AltOkFil.txt")
            (validationResult is ValidationResult.Success) shouldBe true
            (validationResult as ValidationResult.Success).kravLinjer.size shouldBe liste.size - 2
        }

        test("Når validering av fil ikke er OK skal ValidationResult.Error returneres med feilmeldinger") {
            val liste = fileAsList("${File.separator}FtpFiler${File.separator}/FilMedFeilIKontrollLinje.txt")
            val validationResult =	FileValidator(mockk<SlackClient>(relaxed = true)).validateFile(liste, "FilMedFeilIKontrollLinje.txt")
            (validationResult is ValidationResult.Error) shouldBe true
            (validationResult as ValidationResult.Error).messages.size shouldBe 2
        }
    })
