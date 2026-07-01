package no.nav.sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.slackHttpClient
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.dto.slack.ExtraTags
import no.nav.sokos.ske.krav.dto.slack.createSlackMessage
import no.nav.sokos.ske.krav.validation.ErrorCategory

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.slackConfig.url,
    private val client: HttpClient = slackHttpClient,
) {
    suspend fun sendMessage(
        alertTitle: ErrorCategory,
        filename: String,
        extraTags: ExtraTags,
        errorDetails: List<ErrorDetails>,
    ) {
        client.post {
            url(slackEndpoint)
            contentType(ContentType.Application.Json)
            setBody(createSlackMessage(alertTitle, filename, extraTags, errorDetails))
        }
    }
}
