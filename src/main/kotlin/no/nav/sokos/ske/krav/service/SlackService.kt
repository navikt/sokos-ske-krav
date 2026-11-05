package no.nav.sokos.ske.krav.service

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.dto.slack.FileError
import no.nav.sokos.ske.krav.validation.ErrorCategory

class SlackService(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val errorTracking: MutableList<FileError> = mutableListOf()

    fun trackedErrors() = errorTracking.toList()

    fun clearErrorTracking() = errorTracking.clear()

    fun addError(
        filename: String,
        alertTitle: ErrorCategory,
        errorDetails: ErrorDetails,
    ) {
        addErrors(filename, alertTitle, listOf(errorDetails))
    }

    fun addErrors(
        filename: String,
        alertTitle: ErrorCategory,
        errorDetails: List<ErrorDetails>,
    ) {
        errorTracking
            .find { it.filename == filename }
            ?.let { fileError ->
                fileError.alertTitles.add(alertTitle)
                fileError.errorDetails.addAll(errorDetails)
                fileError.updateExtraTags(errorDetails)
            }
            ?: run {
                val fileError =
                    FileError(
                        filename = filename,
                        errorDetails = errorDetails.toMutableList(),
                        alertTitles = mutableSetOf(alertTitle),
                    )
                errorTracking.add(fileError)
            }
    }

    suspend fun sendErrors() {
        errorTracking.forEach { fileError ->
            val allError = mutableListOf<ErrorDetails>()

            fileError.errorDetails
                .groupBy { it.header }
                .forEach { (key, errors) ->
                    if (errors.size > 5) {
                        val caseNumbers = errors.mapNotNull { it.caseNumber }.distinct().joinToString()
                        val consolidatedError =
                            ErrorDetails(
                                header = key,
                                description = "${errors.size} av samme type feil: $key. Sjekk avstemming",
                                caseNumber = caseNumbers,
                            )

                        allError.add(consolidatedError)
                    } else {
                        allError.addAll(errors)
                    }
                }
            slackClient.sendMessage(fileError.createAlertTitle(), fileError.filename, fileError.extraTags, allError)
        }

        clearErrorTracking()
    }

    private fun FileError.createAlertTitle(): String =
        alertTitles
            .sortedBy { it.ordinal }
            .joinToString(separator = " | ") { it.value }
}
