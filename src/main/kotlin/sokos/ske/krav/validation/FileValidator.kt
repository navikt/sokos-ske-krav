package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.metrics.Metrics

object FileValidator {
    private val logger = KotlinLogging.logger("secureLogger")

    fun validateFile(
        content: List<String>,
        fileName: String,
    ): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<String>()

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonsDato != lastLine.transaksjonsDato

        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner} \n")
        if (invalidSum) {
            errorMessages.add(
                "Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n",
            )
        }
        if (invalidTransferDate) {
            errorMessages.add(
                "Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonsDato}\n",
            )
        }

        return if (errorMessages.isNotEmpty()) {
            val err = Metrics.fileValidationError.labelValues(fileName, "$errorMessages")
            err.inc()
            // err.incWithExemplar(1.0, mapOf("fileName" to fileName, "message" to "$errorMessages"))

            //   err.incWithExemplar(1.0, fileName, "$errorMessages")

            // Metrics.fileValidationError.labels(fileName, "$errorMessages").inc()
            logger.warn("*****************Feil i validering av fil $fileName: $errorMessages")
            val res = ValidationResult.Error(messages = errorMessages)
            //  error.inc(500.0)
            return res
        } else {
            ValidationResult.Success(kravLinjer)
        }
    }
}
