package sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.domain.slack.createSlackMessage
import sokos.ske.krav.util.httpClient

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.SlackConfig.url,
    private val client: HttpClient = httpClient,
) {
    suspend fun sendMessage(
        header: String,
        fileName: String,
        messages: Map<String, List<String>>,
    ) {
        client.post {
            url(slackEndpoint)
            contentType(ContentType.Application.Json)
            setBody(createSlackMessage(header, fileName, messages))
        }
    }
}

// TODO bruk junie for å flytte
private data class ErrorHeader(
    val header: String,
    val errors: MutableMap<String, MutableList<String>>,
)

private data class FileErrors(
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
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                slackClient.sendMessage(header.header, fileErrors.fileName, header.errors)
            }
        }
    }
}
