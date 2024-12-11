package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil

class LineValidator(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val logger = KotlinLogging.logger("secureLogger")

    suspend fun validateNewLines(
        file: FtpFil,
        ds: DatabaseService,
    ): List<KravLinje> {
        val messages = mutableMapOf<String, MutableList<String>>()
        val returnLines =
            file.kravLinjer.map { linje ->
                Metrics.numberOfKravRead.increment()

                when (val result: ValidationResult = LineValidationRules.runValidation(linje)) {
                    is ValidationResult.Success -> {
                        linje.copy(status = Status.KRAV_IKKE_SENDT.value)
                    }
                    is ValidationResult.Error -> {
                        result.messages.forEach { pair ->
                            messages.putIfAbsent(pair.first, mutableListOf(pair.second))?.add(pair.second)
                        }
                        ds.saveLineValidationError(file.name, linje, result.messages.joinToString { pair -> pair.second })
                        linje.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }
        sendAlert(file.name, messages)
        if (messages.isNotEmpty()) logger.warn("Feil i validering av linjer i fil ${file.name}: ${messages.keys}")

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

        if (errorMessagesToSend.isNotEmpty()) slackClient.sendLinjevalideringsMelding(filename, errorMessagesToSend)
    }
}
