package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.nav.FileParser

object FileValidator {
    private val logger = KotlinLogging.logger("secureLogger")
    private val  slackClient = SlackClient()

    fun validateFile(
        content: List<String>,
        fileName: String,
    ): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<List<String>>()

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonsDato != lastLine.transaksjonsDato

        if (invalidNumberOfLines) errorMessages.add(listOf("Feil antall linjer i fil", "Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner} \n"))
        if (invalidSum) {
            errorMessages.add(listOf(
                "Sum alle linjer stemmer ikke med sum i siste linje!", "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n",
            ))
        }
        if (invalidTransferDate) {
            errorMessages.add(listOf(
                "Dato sendt er avvikende mellom første og siste linje fra OS!", "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonsDato}\n",
            ))
        }

        return if (errorMessages.isNotEmpty()) {
//            Metrics.registerFileValidationError(fileName, "$errorMessages").increment(1.0)
//            Metrics.registerFileValidationError(fileName, "$errorMessages").increment(1.0)

            slackClient.sendFilvalideringsMelding(fileName, errorMessages)

          /*  val error = Metrics.fileValidationError.labels(fileName, "$errorMessages")
            error.inc(500.0)
            Metrics.fileValidationError.labels(fileName, "$errorMessages").inc()*/
            logger.warn("*****************Feil i validering av fil $fileName: $errorMessages")
            val res = ValidationResult.Error(messages = errorMessages)
            //    error.inc(500.0)
            return res
        } else {
            ValidationResult.Success(kravLinjer)
        }
    }
}
