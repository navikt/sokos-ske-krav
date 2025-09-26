package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.FtpFil

private val logger = mu.KotlinLogging.logger {}

class LineValidator(
    private val slackService: SlackService = SlackService(),
) {
    suspend fun validateNewLines(
        file: FtpFil,
        dbService: DatabaseService,
    ): List<KravLinje> {
        val slackMessages = mutableListOf<Pair<String, String>>()
        val returnLines =
            file.kravLinjer.map { linje ->
                Metrics.numberOfKravRead.increment()

                when (val result: ValidationResult = LineValidationRules.runValidation(linje)) {
                    is ValidationResult.Success -> {
                        linje.copy(status = Status.KRAV_IKKE_SENDT.value)
                    }

                    is ValidationResult.Error -> {
                        slackMessages.addAll(result.messages)

                        dbService.saveLineValidationError(file.name, linje, result.messages.joinToString { pair -> pair.second })
                        linje.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }

        if (slackMessages.isNotEmpty()) {
            logger.warn("Feil i validering av linjer i fil ${file.name}: ${slackMessages.joinToString { it.second }}")
            slackService.addError(file.name, "Feil i linjevalidering", slackMessages)
        }
        slackService.sendErrors()

        return returnLines
    }
}
