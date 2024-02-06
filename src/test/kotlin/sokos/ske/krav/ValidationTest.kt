package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.FileValidator
import sokos.ske.krav.util.ValidationResult

internal class ValidationTest: FunSpec({

  test("Når validering av fil er OK skal ValidationResult.Success returneres med kravlinjene"){
	val liste = readFileFromFS("fil1.txt".asResource())
	val validationResult =	FileValidator.validateFiles(liste)
	(validationResult is ValidationResult.Success) shouldBe true
	(validationResult as ValidationResult.Success).kravLinjer.size shouldBe liste.size - 2
  }

  test("Når validering av fil ikke er OK skal ValidationResult.Error returneres med feilmeldinger"){
	val liste = readFileFromFS("fil3.txt".asResource())
	val validationResult =	FileValidator.validateFiles(liste)
	(validationResult is ValidationResult.Error) shouldBe true
	(validationResult as ValidationResult.Error).messages.size shouldBe 2

  }
})