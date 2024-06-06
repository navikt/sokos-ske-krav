package sokos.ske.krav.service

import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.createEndreHovedstolRequest
import sokos.ske.krav.util.createEndreRenteRequest

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {

    suspend fun sendAllEndreKrav(kravList: List<KravTable>): List<RequestResult> {

        val endringsMap = kravList.groupBy { it.kravidentifikatorSKE + it.saksnummerNAV }

        val resultList = endringsMap.map { entry ->
            val kravidentifikatorPair = createKravidentifikatorPair(entry.value.first())
            val response = entry.value.map {
                sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it)
            }
            getConformedResponses(response)
        }.flatten()

        databaseService.updateSentKrav(resultList)
        return resultList
    }

    private fun getConformedResponses(requestresultList: List<RequestResult>): List<RequestResult> {
        val endring1 = requestresultList.first()
        val endring2 = requestresultList.last()

        if (endring1.status == endring2.status) return requestresultList

        val firstKravStatus = endring1.status
        val secondKravStatus = endring2.status
        val firstHttpStatus = endring1.response.status
        val secondHttpStatus = endring2.response.status

        val newStatus = when {
            firstHttpStatus.value == HttpStatusCode.NotFound.value -> firstKravStatus
            secondHttpStatus.value == HttpStatusCode.NotFound.value -> secondKravStatus
            firstHttpStatus.value == HttpStatusCode.UnprocessableEntity.value -> firstKravStatus
            secondHttpStatus.value == HttpStatusCode.UnprocessableEntity.value -> secondKravStatus
            firstHttpStatus.value == HttpStatusCode.Conflict.value -> firstKravStatus
            secondHttpStatus.value == HttpStatusCode.Conflict.value -> secondKravStatus
            else -> Status.UKJENT_STATUS
        }

        return listOf(
                endring1.copy(status = newStatus),
                endring2.copy(status = newStatus)
        )
    }

    private suspend fun sendEndreKrav(
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        krav: KravTable,
    ): RequestResult {

        return if (krav.kravtype == ENDRING_RENTE) {
            val endreRenterRequest = createEndreRenteRequest(krav)
            val endreRenterResponse =
                skeClient.endreRenter(endreRenterRequest, kravidentifikator, kravidentifikatorType, krav.corrId)

            val requestResultEndreRente = RequestResult(
                response = endreRenterResponse,
                request = Json.encodeToString(endreRenterRequest),
                kravTable = krav,
                kravidentifikator = "",
            )

            requestResultEndreRente

        } else {
            val endreHovedstolRequest = createEndreHovedstolRequest(krav)
            val endreHovedstolResponse =
                skeClient.endreHovedstol(endreHovedstolRequest, kravidentifikator, kravidentifikatorType, krav.corrId)

            val requestResultEndreHovedstol = RequestResult(
                response = endreHovedstolResponse,
                request = Json.encodeToString(endreHovedstolRequest),
                kravTable = krav,
                kravidentifikator = "",
            )

            requestResultEndreHovedstol
        }
    }
}