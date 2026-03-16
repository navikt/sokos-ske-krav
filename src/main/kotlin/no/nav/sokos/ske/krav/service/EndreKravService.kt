package no.nav.sokos.ske.krav.service

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createEndreHovedstolRequest
import no.nav.sokos.ske.krav.util.createEndreRenteRequest
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    /**
     * Sends all "endre krav" requests to SKE and updates the database with results.
     *
     * Groups krav by their unique identifier (kravidentifikatorSKE + saksnummerNAV)
     * and sends requests for each group. When multiple requests are sent for the same
     * krav group, their statuses are conformed to ensure consistency.
     *
     * @param kravList List of krav to send
     * @return List of request results after processing and database update
     */
    suspend fun sendAllEndreKrav(kravList: List<Krav>): List<RequestResult> {
        // Group krav by their unique identifier (SKE identifier + NAV case number)
        val kravGroupedByIdentifier =
            kravList.groupBy { krav ->
                krav.kravidentifikatorSKE + krav.saksnummerNAV
            }

        // Process each group and send requests to SKE
        val requestResults = mutableListOf<RequestResult>()

        for ((_, groupedKrav) in kravGroupedByIdentifier) {
            runCatching { requestResults.addAll(processKravGroup(groupedKrav)) }.onFailure { e ->
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

    /**
     * Processes a group of krav that share the same identifier.
     * Sends individual requests for each krav and conforms their statuses.
     */
    private suspend fun processKravGroup(groupedKrav: List<Krav>): List<RequestResult> {
        val firstKrav = groupedKrav.first()
        val (kravidentifikator, kravidentifikatorType) = createKravidentifikatorPair(firstKrav)

        val requestResults = groupedKrav.map { krav -> sendEndreKrav(kravidentifikator, kravidentifikatorType, krav) }

        // Conform statuses across all requests in the group
        return getConformedResponses(requestResults)
    }

    /**
     * Conforms the status of request results to ensure consistency.
     *
     * When multiple requests are sent for the same krav group (typically 2: one for
     * hovedstol and one for rente), their statuses should be consistent. If they differ,
     * this method determines the most appropriate status based on priority rules.
     *
     * Priority order (highest to lowest):
     * 1. NotFound (404) - Krav doesn't exist
     * 2. UnprocessableEntity (422) - Validation error
     * 3. Conflict (409) - Business rule conflict
     * 4. Other errors
     */
    private fun getConformedResponses(requestResults: List<RequestResult>): List<RequestResult> {
        // If results are empty, or if only one request, or all have same status, no need to conform
        if (requestResults.size < 2) return requestResults

        val firstResult = requestResults.first()
        val lastResult = requestResults.last()

        if (firstResult.status == lastResult.status) return requestResults

        // Determine which status should be used for all results
        val conformedStatus =
            determineNewStatus(
                Pair(firstResult.httpStatusCode.value, firstResult.status),
                Pair(lastResult.httpStatusCode.value, lastResult.status),
            )

        // Apply the conformed status to all results
        return requestResults.map { it.copy(status = conformedStatus) }
    }

    /**
     * Determines which status should take priority when two requests have different statuses.
     *
     * Priority order:
     * 1. NotFound (404) - If either request got 404, the krav doesn't exist
     * 2. UnprocessableEntity (422) - If either has validation errors, that's critical
     * 3. Conflict (409) - If either has business rule conflicts, that takes precedence
     * 4. Unknown status - If none of the above, status is unknown
     *
     * @param endring1 Pair of (HTTP status code, Status) for first request
     * @param endring2 Pair of (HTTP status code, Status) for second request
     * @return The status that should be applied to both requests
     */
    private fun determineNewStatus(
        endring1: Pair<Int, Status>,
        endring2: Pair<Int, Status>,
    ): Status =
        when {
            endring1.first == HttpStatusCode.NotFound.value -> endring1.second
            endring2.first == HttpStatusCode.NotFound.value -> endring2.second
            endring1.first == HttpStatusCode.UnprocessableEntity.value -> endring1.second
            endring2.first == HttpStatusCode.UnprocessableEntity.value -> endring2.second
            endring1.first == HttpStatusCode.Conflict.value -> endring1.second
            endring2.first == HttpStatusCode.Conflict.value -> endring2.second
            else -> Status.UKJENT_STATUS
        }

    private suspend fun sendEndreKrav(
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        krav: Krav,
    ): RequestResult {
        val (response, request) =
            if (krav.kravtype == ENDRING_RENTE) {
                val request = createEndreRenteRequest(krav)
                val response = skeClient.endreRenter(request, kravidentifikator, kravidentifikatorType, krav.corrId)
                Pair(response, request.encodeToString())
            } else {
                val request = createEndreHovedstolRequest(krav)
                val response = skeClient.endreHovedstol(request, kravidentifikator, kravidentifikatorType, krav.corrId)
                Pair(response, request.encodeToString())
            }

        val responseBody = response.bodyAsText()
        val definertStatus = defineStatus(responseBody, response.status)
        return RequestResult(
            responseBody = responseBody,
            httpStatusCode = response.status,
            request = request,
            krav = krav,
            kravidentifikator = "",
            status = definertStatus.first,
            feilResponse = definertStatus.second,
        )
    }
}
