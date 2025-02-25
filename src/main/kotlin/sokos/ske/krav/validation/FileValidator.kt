package sokos.ske.krav.validation

import sokos.ske.krav.client.SlackService
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KontrollLinjeFooter
import sokos.ske.krav.domain.nav.KontrollLinjeHeader
import sokos.ske.krav.domain.nav.KravLinje

class FileValidator(
    private val slackService: SlackService = SlackService(),
) {
    object ErrorKeys {
        const val PARSE_EXCEPTION = "Exception i parsing av fil"
        const val FEIL_I_ANTALL = "Antall krav stemmer ikke med antallet i siste linje"
        const val FEIL_I_SUM = "Sum alle linjer stemmer ikke med sum i siste linje"
        const val FEIL_I_DATO = "Dato sendt er avvikende mellom første og siste linje fra OS"
    }

    suspend fun validateFile(
        content: List<String>,
        fileName: String,
    ): ValidationResult {
        val parser = FileParser(content)

        val errorMessages =
            buildList {
                runCatching {
                    val lastLine = parser.parseKontrollLinjeFooter()
                    val firstLine = parser.parseKontrollLinjeHeader()
                    val kravLinjer =
                        runCatching {
                            parser.parseKravLinjer()
                        }.onFailure {
                            val exceptionMessage = it.message ?: "Ukjent feil"
                            add(ErrorKeys.PARSE_EXCEPTION to exceptionMessage)
                            secureLogger.error(exceptionMessage)
                        }.getOrNull() ?: return@buildList

                    validateLines(lastLine, firstLine, kravLinjer)
                }.onFailure {
                    add(ErrorKeys.PARSE_EXCEPTION to (it.message ?: "Ukjent feil"))
                }
            }

        if (errorMessages.isEmpty()) {
            return ValidationResult.Success(parser.parseKravLinjer())
        }

        secureLogger.warn("*** Feil i validering av fil $fileName ***")

        slackService.addError(fileName, "Feil i validering av fil", errorMessages)
        slackService.sendErrors()

        return ValidationResult.Error(messages = errorMessages)
    }

    private fun MutableList<Pair<String, String>>.validateLines(
        lastLine: KontrollLinjeFooter,
        firstLine: KontrollLinjeHeader,
        kravLinjer: List<KravLinje>,
    ) {
        if (lastLine.antallTransaksjoner != kravLinjer.size) {
            add(ErrorKeys.FEIL_I_ANTALL to "Antall krav: ${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner}\n")
        }
        if (kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner) {
            add(ErrorKeys.FEIL_I_SUM to "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n")
        }
        if (firstLine.transaksjonsDato != lastLine.transaksjonTimestamp) {
            add(ErrorKeys.FEIL_I_DATO to "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonTimestamp}\n")
        }
    }
}
