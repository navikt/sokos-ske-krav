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
import sokos.ske.krav.domain.slack.buildSlackMessage
import sokos.ske.krav.domain.slack.createSlackMessage
import sokos.ske.krav.util.httpClient

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.SlackConfig.url,
    private val client: HttpClient = httpClient,
) {
    suspend fun sendLinjevalideringsMelding(
        fileName: String,
        messages: Map<String, List<String>>,
    ) = doPost(
        createSlackMessage("Feil i linjevalidering", fileName, messages),
    )

    suspend fun sendFilvalideringsMelding(
        fileName: String,
        messages: List<Pair<String, String>>,
    ) = doPost(
        createSlackMessage("Feil i  filvalidering", fileName, messages),
    )

    suspend fun sendAsynkValideringsFeilFraSke(
        fileName: String,
        message: Pair<String, String>,
    ) = doPost(
        createSlackMessage("Asynk valideringsfeil", fileName, listOf(message)),
    )

    suspend fun sendFantIkkeKravidentifikator(
        fileName: String,
        message: Pair<String, String>,
    ) = doPost(
        createSlackMessage("Fant ikke kravidentifikator for migrert krav", fileName, listOf(message)),
    )

    suspend fun sendHttpFeilFraSke(meldinger: List<Pair<String, String>>) =
        doPost(
            buildSlackMessage("Feil i HTTP kall til Skatteetaten", "-NA-", meldinger),
        )

    private suspend fun doPost(data: Data) =
        client.post(
            HttpRequestBuilder().apply {
                url(slackEndpoint)
                contentType(ContentType.Application.Json)
                setBody(data)
            },
        )
}
