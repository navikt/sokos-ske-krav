package no.nav.sokos.ske.krav.service.newversion

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpFil
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_LINJEVALIDERING
import no.nav.sokos.ske.krav.validation.LineValidator
import no.nav.sokos.ske.krav.validation.ValidationResult

private val logger = KotlinLogging.logger {}

class FileHandler(
    private val ftpService: FtpService = FtpService(),
    private val lineValidator: LineValidator = LineValidator(),
    private val dataSource: DataSource = PostgresDataSource.dataSource,
    private val kravRepository: KravRepository = KravRepository.instance,
    private val filValideringsfeilRepository: FilValideringsfeilRepository = FilValideringsfeilRepository.instance,
    private val slackService: SlackService = SlackService(),
) {
    suspend fun processFiles() {
        val files = ftpService.getValidatedFiles()

        if (files.isEmpty()) return

        val fileText = if (files.size == 1) "fil" else "filer"
        val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
        logger.info("*** Starter lagring av ${files.size} $fileText $dateTime***")

        files.forEach { file ->
            processFile(file)
        }

        logger.info { "*** Ferdig med lagring av ${files.size} $fileText ***" }
        slackService.sendErrors()
    }

    private fun processFile(file: FtpFil) {
        logger.info("Antall krav i ${file.name}: ${file.kravLinjer.size}")

        val validatedLines = lineValidator.validateNewLines(file.kravLinjer)
        processValidationResults(file.name, validatedLines)

        ftpService.moveFile(file.name, Directories.INBOUND, Directories.OUTBOUND)
    }

    private fun processValidationResults(
        filename: String,
        validationResults: List<ValidationResult>,
    ) {
        val allKrav = mutableListOf<KravLinje>()
        val invalidKrav = mutableListOf<Pair<KravLinje, String>>()
        val slackMessages = mutableListOf<ErrorDetails>()

        validationResults.forEach { result ->
            when (result) {
                is ValidationResult.Error -> {
                    slackMessages.addAll(result.errors)
                    result.originalLines?.forEach { line ->
                        invalidKrav.add(line to result.errors.joinToString { it.description })
                        allKrav.add(line)
                    }
                }
                is ValidationResult.Success -> {
                    allKrav.addAll(result.kravLinjer)
                }
            }
        }

        if (invalidKrav.isNotEmpty()) {
            logger.warn("Ved validering av linjer i fil $filename har ${invalidKrav.size} linjer valideringsfeil ")
        }

        if (slackMessages.isNotEmpty()) {
            logger.warn("Feil i validering av linjer i fil $filename: ${slackMessages.joinToString { it.description }}")
            slackService.addErrors(filename, FEIL_I_LINJEVALIDERING, slackMessages)
        }

        dataSource.transaction { session ->
            kravRepository.insertAllNewKrav(session, allKrav, filename)
            filValideringsfeilRepository.insertAllLineFilValideringsfeil(session, filename, invalidKrav)
        }
    }
}
