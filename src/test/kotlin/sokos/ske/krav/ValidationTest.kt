package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.FileParser
import sokos.ske.krav.util.FileValidator
import sokos.ske.krav.util.LineValidator
import sokos.ske.krav.util.ValidationResult
import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.readFileFromFS

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
	FileParser(liste).parseKravLinjer().map { LineValidator.validateLine(it, "AltOkFil.txt")}.size shouldBe liste.size - 2
  }

  test("Validering av linje skal returnere false når validering ikke er OK") {
	val liste = readFileFromFS("FilMedFeilKravKode.txt".asResource())
	FileParser(liste).parseKravLinjer().filter { !LineValidator.validateLine(it, "FilMedFeilKravKode.txt") }.size shouldBe 1
  }


  test("Validering av linje skal feile når kravtypen er ugyldig") {
	  val liste = readFileFromFS("FilMedFeilKravKode.txt".asResource())
	  FileParser(liste).parseKravLinjer().filter { !LineValidator.validateLine(it, "FilMedFeilKravKode.txt") }.size shouldBe 1
  }
  test("Validering av linje skal feile når beløpet er null") {
    val liste = readFileFromFS("FilMedBelopLikNull.txt".asResource())
    FileParser(liste).parseKravLinjer().filter { !LineValidator.validateLine(it, "FilMedBelopLikNull.txt") }.size shouldBe 2
  }


  test("Rentebeløp er ikke over null") {}
  test("Tom oppdragsgivers referanse") {}
  test("Ugyldig fastsettelsesdato") {}
  test("Ugyldig foreldelsesfristens utgangspunkt") {}
  test("Ugyldig renter ilagt dato:") {}
  test("Ytelser for avregning er ikke over null") {}
  test("Ugyldig tilbakekrevingsperiode") {}
  test("Hovedstol er ikke over null") {}
  test("Rentebeløp er under null") {}
})