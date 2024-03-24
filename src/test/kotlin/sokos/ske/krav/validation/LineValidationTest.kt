package sokos.ske.krav.validation

import io.kotest.core.spec.style.FunSpec
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.readFileFromFS

internal class LineValidationTest: FunSpec({

  test("Validering av linje skal returnere true når validering er ok") {
	val liste = readFileFromFS("AltOkFil.txt".asResource())
    val fil = FtpFil(
        this.testCase.name.testName,
        liste,
        kravLinjer = FileParser(liste).parseKravLinjer()
    )
//        LineValidator.validateNewLines(fil).size shouldBe liste.size - 2
  }

  test("Validering av linje skal feile når kravtypen er ugyldig") {
	val liste = readFileFromFS("FilMedFeilKravKode.txt".asResource())
      val fil = FtpFil(
          this.testCase.name.testName,
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
//      LineValidator.validateNewLines(fil).size shouldBe liste.size - 3
  }


  test("Beløp kan ikke være 0 når det er nytt krav eller krav som skal endres") {
    val liste = readFileFromFS("FilMedBelopLikNull.txt".asResource())
      val fil = FtpFil(
          this.testCase.name.testName,
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
//      LineValidator.validateNewLines(fil).size shouldBe 8
  }

    test("Saksnummer må være riktig formatert"){
        val liste = readFileFromFS("FilMedFeilSaksnr.txt".asResource())
        val fil = FtpFil(
            this.testCase.name.testName,
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
//        LineValidator.validateNewLines(fil).size shouldBe 1
    }

    test("Refnummer gamme sak må være riktig formatert"){
        val liste = readFileFromFS("FilMedFeilRefnrGmlSak.txt".asResource())
        val fil = FtpFil(
            this.testCase.name.testName,
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
//        LineValidator.validateNewLines(fil).size shouldBe 2
    }

    test("Vedtaksdato kan ikke være i fremtiden" ){
        val liste = readFileFromFS("FilMedUgyldigVedtaksdato.txt".asResource())
        val fil = FtpFil(
            this.testCase.name.testName,
            liste,
            kravLinjer = FileParser(liste).parseKravLinjer()
        )
//        LineValidator.validateNewLines(fil).size shouldBe 3
    }

  test("Periode må være i fortid og fom må være før tom") {
      val liste = readFileFromFS("FilMedFeilPeriode.txt".asResource())
      val fil = FtpFil(
          this.testCase.name.testName,
          liste,
          kravLinjer = FileParser(liste).parseKravLinjer()
      )
//      LineValidator.validateNewLines(fil).size shouldBe 3
  }

})