package no.nav.sokos.ske.krav.service

import io.ktor.http.HttpStatusCode

import no.nav.sokos.ske.krav.client.SkeClient
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
    suspend fun sendAllEndreKrav(kravList: List<Krav>): List<RequestResult> =
        kravList
            .groupBy { it.kravidentifikatorSKE + it.saksnummerNAV }
            .flatMap { (_, groupedKrav) ->
                val kravidentifikatorPair = createKravidentifikatorPair(groupedKrav.first())
                val response =
                    groupedKrav.map {
                        sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it)
                    }
                getConformedResponses(response)
            }.also { databaseService.updateSentKrav(it) }

    private fun getConformedResponses(requestResults: List<RequestResult>): List<RequestResult> {
        val endring1 = requestResults.first()
        val endring2 = requestResults.last()

        if (endring1.status == endring2.status) return requestResults

        val newStatus =
            determineNewStatus(
                Pair(endring1.response.status.value, endring1.status),
                Pair(endring2.response.status.value, endring2.status),
            )

        return listOf(endring1, endring2).map { it.copy(status = newStatus) }
    }

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

        return RequestResult(
            response = response,
            request = request,
            krav = krav,
            kravidentifikator = "",
            status = defineStatus(response),
        )
    }
}
