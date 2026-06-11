package no.nav.sokos.ske.krav.validation

import javax.sql.DataSource

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.service.FtpFil
import no.nav.sokos.ske.krav.util.transaction

private val logger = mu.KotlinLogging.logger {}

class LineValidator(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
    private val filValideringsfeilRepository: FilValideringsfeilRepository,
    private val slackService: SlackService = SlackService(),
) {
    suspend fun validateNewLines(file: FtpFil): List<KravLinje> {
        val slackMessages = mutableListOf<Pair<String, String>>()
        val returnLines =
            file.kravLinjer.map { linje ->
                Metrics.numberOfKravRead.increment()

                when (val result: ValidationResult = LineValidationRules.runValidation(linje)) {
                    is ValidationResult.Success -> {
                        linje.markAsValid()
                    }

                    is ValidationResult.Error -> {
                        slackMessages.addAll(result.messages.map { it.first.value to it.second })

                        dataSource.transaction { session ->
                            filValideringsfeilRepository.insertLineFilValideringsfeil(session, file.name, linje, result.messages.joinToString { pair -> pair.second })
                        }
                        linje.markAsValidationError()
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
