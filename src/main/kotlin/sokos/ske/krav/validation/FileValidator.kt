package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.nav.FileParser

class FileValidator(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val logger = KotlinLogging.logger("secureLogger")

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

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonsDato != lastLine.transaksjonsDato

        val errorMessages =
            buildList {
                if (invalidNumberOfLines) {
                    add(
                        Pair(
                            ErrorKeys.FEIL_I_ANTALL,
                            "Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner}\n",
                        ),
                    )
                }
                if (invalidSum) {
                    add(
                        Pair(
                            ErrorKeys.FEIL_I_SUM,
                            "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n",
                        ),
                    )
                }
                if (invalidTransferDate) {
                    add(
                        Pair(
                            ErrorKeys.FEIL_I_DATO,
                            "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonsDato}\n",
                        ),
                    )
                }
            }

        return if (errorMessages.isNotEmpty()) {
            slackClient.sendFilvalideringsMelding(fileName, errorMessages)
            logger.warn("*** Feil i validering av fil $fileName. Sjekk Slack og Database ***")
            ValidationResult.Error(messages = errorMessages)
        } else {
            ValidationResult.Success(kravLinjer)
        }
    }
}
