package sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import sokos.ske.krav.util.httpClient
import java.util.Calendar

class SlackClient(
    private val client: HttpClient = httpClient,
) {
    private val slackEndpoint = "https://hooks.slack.com/services/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"

    suspend fun doPost(
        feiltype: String,
        filnavn: String,
        feilmelding: String,
    ) = client.post(
        HttpRequestBuilder().apply {
            url("$slackEndpoint")
            contentType(ContentType.Application.Json)
            setBody(request(feiltype, filnavn, feilmelding))
        },
    )
}

private fun request(
    feiltype: String,
    filnavn: String,
    feilmelding: String,
) = """{ 
"blocks": [ 
    {
        "type": "header",
        "text": {
        "type": "plain_text",
        "text": "*FEIL VED VALIDERING AV FIL*",
        "emoji": true
    }
},{
    "type": "section",
    "fields": [
        {
            "type": "mrkdwn",
            "text": "*Feil:*\n$feiltype"
        },
        {
            "type": "mrkdwn",
            "text": "*Filnavn:*\n$filnavn"
        }
    ]
}, {
    "type": "section",
    "fields": [
        {
            "type": "mrkdwn",
            "text": "*Feilmelding:*\n$feilmelding"
        }
    ]
}, {
    "type": "section",
    "fields": [
        {
            "type": "mrkdwn",
            "text": "*Når:*\n${Calendar.getInstance().time}"
        }
    ]
}, {
    "type": "section",
    "fields": [
        {
            "type": "mrkdwn",
            "text": "*Løses av*\n<@U03EEJ1EQ1W>"
        },
        {
            "type": "mrkdwn",
            "text": "*Varsles når løst*\n<@UA7DM5UHG>"
        }
    ]
} ] }"""
