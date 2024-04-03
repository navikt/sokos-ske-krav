package sokos.ske.krav.service

import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.util.*

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {

    suspend fun sendAllEndreKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {

        val endringsMap = kravList.groupBy { it.saksnummerSKE + it.saksnummerNAV }

        val resultList = endringsMap.map { entry ->

            val kravidentifikatorPair = createKravidentifikatorPair(entry.value.first())
            val response = entry.value.map {
                sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it)
            }
            getConformedResponses(response)
        }.flatten()

        resultList.forEach { it.values.forEach { value ->  println(value.status) }}
        databaseService.updateSentKrav(resultList)
        return resultList
    }



    private fun getConformedResponses(inMapList: List<Map<String, RequestResult>>): List<Map<String, RequestResult>> {
        val endring1 = inMapList.first().values
        val endring2 = inMapList.last().values

        if (endring1.first().status == endring2.first().status) return inMapList

        val firstKravStatus = endring1.first().status
        val secondKravStatus = endring2.last().status
        val firstHttpStatus = endring1.first().response.status
        val secondHttpStatus = endring2.last().response.status

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
            mapOf(
                inMapList.first().keys.first() to endring1.first().copy(status = newStatus),
                inMapList.last().keys.last() to endring2.last().copy(status = newStatus)
            )
        )
    }

    private suspend fun sendEndreKrav(
        kravidentifikator: String,
        kravidentifikatorType: KravidentifikatorType,
        krav: KravTable,
    ): Map<String, RequestResult> {

        return if (krav.kravtype == ENDRING_RENTE) {
            val endreRenterRequest = makeEndreRenteRequest(krav)
            val endreRenterResponse =
                skeClient.endreRenter(endreRenterRequest, kravidentifikator, kravidentifikatorType, krav.corr_id)

            val requestResultEndreRente = RequestResult(
                response = endreRenterResponse,
                request = Json.encodeToString(endreRenterRequest),
                krav = krav,
                kravIdentifikator = "",
            )

            mapOf(ENDRING_RENTE to requestResultEndreRente)

        } else {
            val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
            val endreHovedstolResponse =
                skeClient.endreHovedstol(endreHovedstolRequest, kravidentifikator, kravidentifikatorType, krav.corr_id)

            val requestResultEndreHovedstol = RequestResult(
                response = endreHovedstolResponse,
                request = Json.encodeToString(endreHovedstolRequest),
                krav = krav,
                kravIdentifikator = "",
            )

            mapOf(ENDRING_HOVEDSTOL to requestResultEndreHovedstol)

        }
    }
}