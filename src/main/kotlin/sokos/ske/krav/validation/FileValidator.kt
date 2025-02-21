package sokos.ske.krav.validation

import sokos.ske.krav.client.SlackService
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.domain.nav.FileParser

class FileValidator(
    private val slackService: SlackService = SlackService(),
) {
    object ErrorKeys {
        const val FEIL_I_ANTALL = "Antall krav stemmer ikke med antallet i siste linje"
        const val FEIL_I_SUM = "Sum alle linjer stemmer ikke med sum i siste linje"
        const val FEIL_I_DATO = "Dato sendt er avvikende mellom første og siste linje fra OS"
    }

    suspend fun validateFile(
        content: List<String>,
        fileName: String,
    ): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages =
            buildList {
                if (lastLine.antallTransaksjoner != kravLinjer.size) {
                    add(Pair(ErrorKeys.FEIL_I_ANTALL, "Antall krav: ${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner}\n"))
                }
                if (kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner) {
                    add(Pair(ErrorKeys.FEIL_I_SUM, "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n"))
                }
                if (firstLine.transaksjonsDato != lastLine.transaksjonTimestamp) {
                    add(Pair(ErrorKeys.FEIL_I_DATO, "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonTimestamp}\n"))
                }
            }

        return if (errorMessages.isNotEmpty()) {
            slackService.addError(fileName, "Feil i validering av fil", errorMessages)
            slackService.sendErrors()

            secureLogger.warn("*** Feil i validering av fil $fileName. Sjekk Slack og Database ***")
            ValidationResult.Error(messages = errorMessages)
        } else {
            ValidationResult.Success(kravLinjer)
        }
    }
}
