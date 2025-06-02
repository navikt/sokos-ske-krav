package no.nav.sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.httpClient
import no.nav.sokos.ske.krav.domain.slack.createSlackMessage

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
