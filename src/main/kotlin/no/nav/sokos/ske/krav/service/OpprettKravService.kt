package no.nav.sokos.ske.krav.service

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.decodeTo
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString

private val logger = mu.KotlinLogging.logger {}

class OpprettKravService(
    private val skeClient: SkeClient,
) {
    suspend fun sendAllOpprettKrav(kravList: List<Krav>): List<RequestResult> {
        val requestResults = mutableListOf<RequestResult>()

        for (krav in kravList) {
            runCatching { requestResults.add(sendOpprettKrav(krav)) }.onFailure { e ->
                if (e is CircuitBreakerException || e is CallNotPermittedException) {
                    logger.info { "Circuit breaker is open, stopping sending krav. Exception: ${e.message}" }
                    break
                } else {
                    throw e
                }
            }
        }
        return requestResults
    }

    private suspend fun sendOpprettKrav(krav: Krav): RequestResult {
        logger.info { "sendOpprettKrav" }
        val opprettKravRequest = createOpprettKravRequest(krav)
        logger.info { opprettKravRequest }
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)
        logger.info { "Response status: ${response.status}" }
        logger.info { "Response request time: ${response.requestTime}" }
        val responseBody = response.bodyAsText()
        logger.info { responseBody }
        val definertStatus = defineStatus(responseBody, response.status)
        val kravidentifikator = if (response.status.isSuccess()) responseBody.decodeTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: "" else ""

        return RequestResult(
            responseBody = responseBody,
            httpStatusCode = response.status,
            request = opprettKravRequest.encodeToString(),
            krav = krav,
            kravidentifikator = kravidentifikator,
            status = definertStatus.first,
            feilResponse = definertStatus.second,
        )
    }
}
