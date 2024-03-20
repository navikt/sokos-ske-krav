package sokos.ske.krav.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.readFileFromFS

internal class FileValidatorTest: FunSpec({

    test("Når validering av fil er OK skal ValidationResult.Success returneres med kravlinjene"){
        val liste = readFileFromFS("endringAvNye.txt".asResource())
        val validationResult =	FileValidator.validateFile(liste, "AltOkFil.txt")
        (validationResult is ValidationResult.Success) shouldBe true
        (validationResult as ValidationResult.Success).kravLinjer.size shouldBe liste.size - 2
    }

    test("Når validering av fil ikke er OK skal ValidationResult.Error returneres med feilmeldinger"){
        val liste = readFileFromFS("FilMedFeilIKontrollLinje.txt".asResource())
        val validationResult =	FileValidator.validateFile(liste, "FilMedFeilIKontrollLinje.txt")
        (validationResult is ValidationResult.Error) shouldBe true
        (validationResult as ValidationResult.Error).messages.size shouldBe 2
    }


})
