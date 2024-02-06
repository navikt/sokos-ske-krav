package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.FilParser
import sokos.ske.krav.util.FileValidator
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.ValidationResult

internal class ValidationTest: FunSpec({

  test("Når validering av fil er OK skal ValidationResult.Success returneres med kravlinjene"){
	val liste = readFileFromFS("AltOkFil.txt".asResource())
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

  test("Validering av linje skal returnere true når validering er ok") {
	val liste = readFileFromFS("AltOkFil.txt".asResource())
	FilParser(liste).parseKravLinjer().map { LineValidator.validateLine(it, "AltOkFil.txt")}.size shouldBe liste.size - 2
  }

  test("Validering av linje skal returnere false når validering ikke er OK") {
	val liste = readFileFromFS("FilMedFeilKravKode.txt".asResource())
	FilParser(liste).parseKravLinjer().filter { !LineValidator.validateLine(it, "FilMedFeilKravKode.txt") }.size shouldBe 1
  }
  test("Validering av linje skal returnere true når validering er OK") {
	val liste = readFileFromFS("AltOkFil.txt".asResource())
	FilParser(liste).parseKravLinjer().filter { LineValidator.validateLine(it, "AltOkFil.txt") }.size shouldBe liste.size - 2
  }
})