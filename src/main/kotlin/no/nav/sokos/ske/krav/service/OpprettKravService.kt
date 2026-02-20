package no.nav.sokos.ske.krav.service

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.jsonConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString
import no.nav.sokos.ske.krav.util.logger

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    suspend fun sendAllOpprettKrav(kravList: List<Krav>): List<RequestResult> {
        val requestResults = mutableListOf<RequestResult>()

        for (krav in kravList) {
            runCatching { requestResults.add(sendOpprettKrav(krav)) }.onFailure { e ->
                if (e is CircuitBreakerException || e is CallNotPermittedException) {
                    break
                } else {
                    throw e
                }
            }
        }
        databaseService.updateSentKrav(requestResults)
        return requestResults
    }

    // Kun for å få tester til å passe
    fun decodeToOpprettInnkrevingsOppdragResponse(response: String): OpprettInnkrevingsOppdragResponse = jsonConfig.decodeFromString<OpprettInnkrevingsOppdragResponse>(response)

    private suspend fun sendOpprettKrav(krav: Krav): RequestResult {
        val opprettKravRequest = createOpprettKravRequest(krav)
        val response: HttpResponse = skeClient.opprettKrav(opprettKravRequest, krav.corrId)
        val responseBody: String = response.bodyAsText()
        logger.info(marker = TEAM_LOGS_MARKER) { "Response from SKE: $responseBody" }
        val status = defineStatus(responseBody, response.status)
        val kravidentifikator =
            if (response.status.isSuccess()) {
                try {
                    decodeToOpprettInnkrevingsOppdragResponse(responseBody).kravidentifikator
                } catch (e: Exception) {
                    ""
                }
            } else {
                ""
            }

        return RequestResult(
            response = response,
            request = opprettKravRequest.encodeToString(),
            krav = krav,
            kravidentifikator = kravidentifikator,
            status = status,
        )
    }
}
