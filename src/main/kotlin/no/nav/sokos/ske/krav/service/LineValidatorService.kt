package no.nav.sokos.ske.krav.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.nav.FtpFilDTO
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.ValideringsfeilRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.validation.LineValidationRules
import no.nav.sokos.ske.krav.validation.ValidationResult

private val logger = KotlinLogging.logger {}

class LineValidatorService(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
    private val valideringsfeilRepository: ValideringsfeilRepository = ValideringsfeilRepository(dataSource),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun validateNewLines(ftpFilDTO: FtpFilDTO): List<KravLinje> {
        val slackMessages = mutableListOf<Pair<String, String>>()
        return runCatching {
            val returnLines =
                ftpFilDTO.kravLinjer.map { linje ->
                    Metrics.numberOfKravRead.increment()

                    when (val result: ValidationResult = LineValidationRules.runValidation(linje)) {
                        is ValidationResult.Success -> {
                            linje.copy(status = Status.KRAV_IKKE_SENDT.value)
                        }

                        is ValidationResult.Error -> {
                            slackMessages.addAll(result.messages)

                            dataSource.transaction { session ->
                                valideringsfeilRepository.insertLineValideringsfeil(ftpFilDTO.name, linje, result.messages.joinToString { pair -> pair.second }, session)
                            }
                            linje.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                        }
                    }
                }

            if (slackMessages.isNotEmpty()) {
                logger.warn("Feil i validering av linjer i fil ${ftpFilDTO.name}: ${slackMessages.joinToString { it.second }}")
                slackService.addError(ftpFilDTO.name, "Feil i linjevalidering", slackMessages)
            }
            slackService.sendErrors()

            returnLines
        }.onFailure { exception ->
            logger.error { exception.message }
        }.getOrThrow()
    }
}
