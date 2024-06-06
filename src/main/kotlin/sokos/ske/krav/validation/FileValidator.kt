package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.metrics.Metrics
import java.util.*

object FileValidator{
    private val logger = KotlinLogging.logger("secureLogger")
    fun validateFile(content: List<String>, fileName: String): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<String>()

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonsDato != lastLine.transaksjonsDato

        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner} ")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonsDato}")


        return if (errorMessages.isNotEmpty()) {
            Metrics.fileValidationError.labels("$fileName${UUID.randomUUID()}", "$errorMessages${UUID.randomUUID()}").inc()
            logger.info("*****************Feil i validering av fil $fileName: $errorMessages")
            return ValidationResult.Error(messages = errorMessages)
        } else {
            ValidationResult.Success(kravLinjer)
        }
    }
}

