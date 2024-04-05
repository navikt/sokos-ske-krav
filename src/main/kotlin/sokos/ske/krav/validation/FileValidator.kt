package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.metrics.Metrics



object FileValidator{
    private val logger = KotlinLogging.logger(this.javaClass.name)
    fun validateFile(content: List<String>, fileName: String): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<String>()

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonDato != lastLine.transaksjonDato

        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner} ")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transaksjonDato}, Dato siste linje: ${lastLine.transaksjonDato}")


        if (errorMessages.isNotEmpty()){
          Metrics.fileValidationError.labels(fileName, errorMessages.toString()).inc()
          logger.info ("Feil i validering av fil $fileName: $errorMessages" )
          return ValidationResult.Error(messages = errorMessages)
        }

        return ValidationResult.Success(kravLinjer)
    }
}
