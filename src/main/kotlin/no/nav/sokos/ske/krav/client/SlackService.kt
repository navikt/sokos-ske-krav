package no.nav.sokos.ske.krav.client

import no.nav.sokos.ske.krav.domain.SlackTags
import no.nav.sokos.ske.krav.domain.TaggablePeople

internal data class ErrorHeader(
    val header: String,
    val errors: MutableMap<String, MutableList<String>>,
)

internal data class FileErrors(
    val fileName: String,
    val headers: MutableList<ErrorHeader>,
    val saksnummer: String? = null,
)

class SlackService(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val errorTracking: MutableList<FileErrors> = mutableListOf()

    fun addError(
        fileName: String,
        header: String,
        messages: Map<String, List<String>>,
        saksnummer: String? = null,
    ) {
        val fileError =
            errorTracking.find { it.fileName == fileName }
                ?: FileErrors(fileName, mutableListOf(), saksnummer).also { errorTracking.add(it) }

        val headerEntry =
            fileError.headers.find { it.header == header }
                ?: ErrorHeader(header, mutableMapOf()).also { fileError.headers.add(it) }

        messages.forEach { (errorType, errorMessages) ->
            val errorTypeMessages = headerEntry.errors.getOrPut(errorType) { mutableListOf() }
            errorTypeMessages.addAll(errorMessages)
        }
    }

    fun addError(
        fileName: String,
        header: String,
        messages: Pair<String, String>,
        saksnummer: String? = null,
    ) {
        val map = mapOf(messages.first to listOf(messages.second))
        addError(fileName, header, map, saksnummer)
    }

    fun addError(
        fileName: String,
        header: String,
        messages: List<Pair<String, String>>,
        saksnummer: String? = null, // TODO: I en annen branch, refaktorer validator slik at saksnummer kan sendes inn
    ) {
        val map = messages.groupBy({ it.first }, { it.second })
        addError(fileName, header, map, saksnummer)
    }

    private fun consolidateErrors() {
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                header.errors.forEach { (errorType, messages) ->
                    if (messages.size > 5) {
                        header.errors[errorType] =
                            mutableListOf(
                                "${messages.size} av samme type feil: $errorType. Sjekk avstemming",
                            )
                    }
                }
            }
        }
    }

    suspend fun sendErrors() {
        consolidateErrors()
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                val matchedTags = header.errors.keys.map { errorType -> SlackTags.lookupMap[errorType] }

                val taggedPeople = matchedTags.flatMap { it?.personer ?: listOf(TaggablePeople.LENE) }.distinct()
                val rutineLink = matchedTags.firstNotNullOfOrNull { it?.rutineLink }
                slackClient.sendMessage(header.header, fileErrors.fileName, header.errors, taggedPeople, rutineLink, fileErrors.saksnummer ?: "")
            }
        }

        errorTracking.clear()
    }
}
