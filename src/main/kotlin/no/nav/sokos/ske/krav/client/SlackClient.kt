package no.nav.sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.slackHttpClient
import no.nav.sokos.ske.krav.domain.TaggablePeople
import no.nav.sokos.ske.krav.dto.slack.createSlackMessage

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.slackConfig.url,
    private val client: HttpClient = slackHttpClient,
) {
    suspend fun sendMessage(
        header: String,
        fileName: String,
        messages: Map<String, List<String>>,
        taggedPeople: List<TaggablePeople> = emptyList(),
        rutineLink: String? = null,
    ) {
        client
            .post {
                url(slackEndpoint)
                contentType(ContentType.Application.Json)
                setBody(createSlackMessage(header, fileName, messages, taggedPeople, rutineLink))
            }
    }
}
