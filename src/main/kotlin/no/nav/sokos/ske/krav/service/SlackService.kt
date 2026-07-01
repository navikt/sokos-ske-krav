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
            .find { it.filename == filename && it.alertTitle == alertTitle }
            ?.let { fileError ->
                fileError.errorDetails.addAll(errorDetails)
                fileError.updateExtraTags(errorDetails)
            }
            ?: run {
                val fileError =
                    FileError(
                        alertTitle = alertTitle,
                        filename = filename,
                        errorDetails = errorDetails.toMutableList(),
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
                        val consolidatedError =
                            ErrorDetails(
                                header = key,
                                description = "${errors.size} av samme type feil: $key. Sjekk avstemming",
                                caseNumber = errors.joinToString { it.caseNumber ?: "" },
                            )

                        allError.add(consolidatedError)
                    } else {
                        allError.addAll(errors)
                    }
                }
            slackClient.sendMessage(fileError.alertTitle, fileError.filename, fileError.extraTags, allError)
        }

        clearErrorTracking()
    }
}
