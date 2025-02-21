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
