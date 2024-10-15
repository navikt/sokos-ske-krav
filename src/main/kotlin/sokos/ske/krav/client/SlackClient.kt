package sokos.ske.krav.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.domain.slack.Data
import sokos.ske.krav.domain.slack.message
import sokos.ske.krav.util.httpClient

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.SlackConfig.url,
    private val client: HttpClient = httpClient,
) {
    suspend fun sendFilvalideringsMelding(
        filnavn: String,
        meldinger: List<List<String>>,
    ) = doPost(
        message("Feil i  filvalidering for sokos-ske-krav", filnavn, meldinger),
    )

    suspend fun sendLinjevalideringsMelding(
        filnavn: String,
        meldinger: List<List<String>>,
    ) = doPost(
        message("Feil i linjevalidering for sokos-ske-krav", filnavn, meldinger),
    )

    suspend fun sendValideringsfeilFraSke(meldinger: List<List<String>>) =
        doPost(
            message("Valideringsfeil hos Skatteetaten ved sending fra sokos-ske-krav", "-NA-", meldinger),
        )

    suspend fun doPost(data: Data) =
        client.post(
            HttpRequestBuilder().apply {
                url(slackEndpoint)
                contentType(ContentType.Application.Json)
                setBody(data)
            },
        )
}
