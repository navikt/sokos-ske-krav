package sokos.ske.krav.client

internal data class ErrorHeader(
    val header: String,
    val errors: MutableMap<String, MutableList<String>>,
)

internal data class FileErrors(
    val fileName: String,
    val headers: MutableList<ErrorHeader>,
)

class SlackService(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val errorTracking: MutableList<FileErrors> = mutableListOf()

    fun addError(
        fileName: String,
        header: String,
        messages: Map<String, List<String>>,
    ) {
        val fileError =
            errorTracking.find { it.fileName == fileName }
                ?: FileErrors(fileName, mutableListOf()).also { errorTracking.add(it) }

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
    ) {
        val map = mapOf(messages.first to listOf(messages.second))
        addError(fileName, header, map)
    }

    fun addError(
        fileName: String,
        header: String,
        messages: List<Pair<String, String>>,
    ) {
        val map = messages.groupBy({ it.first }, { it.second })
        addError(fileName, header, map)
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
        println("sending ${errorTracking.size} errors")
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                slackClient.sendMessage(header.header, fileErrors.fileName, header.errors)
            }
        }

        errorTracking.clear()
    }
}
