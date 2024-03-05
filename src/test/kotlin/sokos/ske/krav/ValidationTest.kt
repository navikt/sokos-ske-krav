package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.FileParser
import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.readFileFromFS
import sokos.ske.krav.validation.FileValidator
import sokos.ske.krav.validation.LineValidator
import sokos.ske.krav.validation.ValidationResult

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
    val fil = FtpFil(
        "hei.txt",
        liste,
        kravLinjer = FileParser(liste).parseKravLinjer()
    )
        LineValidator.getOkLines(fil).size shouldBe liste.size - 2
  }

  test("Validering av linje skal feile når kravtypen er ugyldig") {
	val liste = readFileFromFS("FilMedFeilKravKode.txt".asResource())
      val fil = FtpFil(
          "hei.txt",
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
      LineValidator.getOkLines(fil).size shouldBe liste.size - 3
  }


  test("Beløp kan ikke være 0 når det er nytt krav eller krav som skal endres") {
    val liste = readFileFromFS("FilMedBelopLikNull.txt".asResource())
      val fil = FtpFil(
          "hei.txt",
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
      LineValidator.getOkLines(fil).size shouldBe 8
  }

    test("Saksnummer må være riktig formatert"){
        val liste = readFileFromFS("FilMedFeilSaksnr.txt".asResource())
        val fil = FtpFil(
            "hei.txt",
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
        LineValidator.getOkLines(fil).size shouldBe 1
    }

    test("Refnummer gamme sak må være riktig formatert"){
        val liste = readFileFromFS("FilMedFeilRefnrGmlSak.txt".asResource())
        val fil = FtpFil(
            "hei.txt",
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
        LineValidator.getOkLines(fil).size shouldBe 2
    }

    test("Vedtaksdato kan ikke være i fremtiden" ){
        val liste = readFileFromFS("FilMedUgyldigVedtaksdato.txt".asResource())
        val fil = FtpFil(
            "hei.txt",
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
        LineValidator.getOkLines(fil).size shouldBe 3
    }

  test("Periode må være i fortid og fom må være før tom") {
      val liste = readFileFromFS("FilMedFeilPeriode.txt".asResource())
      val fil = FtpFil(
          "hei.txt",
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
      LineValidator.getOkLines(fil).size shouldBe 2
  }
})