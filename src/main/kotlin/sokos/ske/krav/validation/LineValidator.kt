package sokos.ske.krav.validation

import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil

class LineValidator(
    private val slackClient: SlackClient = SlackClient(),
) {
    suspend fun validateNewLines(
        file: FtpFil,
        dbService: DatabaseService,
    ): List<KravLinje> {
        val slackMessages = mutableMapOf<String, MutableList<String>>()
        val returnLines =
            file.kravLinjer.map { linje ->
                Metrics.numberOfKravRead.increment()

                when (val result: ValidationResult = LineValidationRules.runValidation(linje)) {
                    is ValidationResult.Success -> {
                        linje.copy(status = Status.KRAV_IKKE_SENDT.value)
                    }
                    is ValidationResult.Error -> {
                        result.messages.forEach { pair ->
                            slackMessages.putIfAbsent(pair.first, mutableListOf(pair.second))?.add(pair.second)
                        }
                        // TODO: Hvorfor ikke lagre hver feilmelding separat? altså flere database entries per linje
                        dbService.saveLineValidationError(file.name, linje, result.messages.joinToString { pair -> pair.second })
                        linje.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }

        if (slackMessages.isNotEmpty()) {
            secureLogger.warn("Feil i validering av linjer i fil ${file.name}: ${slackMessages.keys}")
            sendAlert(file.name, slackMessages)
        }

        return returnLines
    }

    private suspend fun sendAlert(
        filename: String,
        errors: Map<String, List<String>>,
    ) {
        val errorMessagesToSend =
            buildMap {
                errors.filter { it.value.size > 5 }.forEach {
                    putIfAbsent("${it.value.size} av samme type feil: ${it.key}", listOf("Sjekk Database og logg"))
                }
                putAll(errors.filterNot { it.value.size > 5 })
            }

        if (errorMessagesToSend.isNotEmpty()) slackClient.sendMessage("Feil i linjevalidering", filename, errorMessagesToSend)
    }
}
