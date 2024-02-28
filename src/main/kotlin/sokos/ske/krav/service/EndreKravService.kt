package sokos.ske.krav.service

import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.util.*

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllEndreKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {

        kravList.groupBy({ it.saksnummerSKE + it.referanseNummerGammelSak + it.belop.toString() })

        val endringsMap = kravList.groupBy { it.saksnummerSKE + it.saksnummerNAV }

        val resultList = endringsMap.map { entry ->
            val kravidentifikatorPair = createKravidentifikatorPair(entry.value.first())
            val response = entry.value.map {
                sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it)
            }
            getConformedResponses(response)
        }
        return resultList.flatten()
    }

    private fun getConformedResponses(inMapList: List<Map<String, RequestResult>>): List<Map<String, RequestResult>> {

        if (inMapList.first().values.first().status == inMapList.last().values.first().status) return inMapList

        val firstKravStatus = inMapList.first().values.first().status
        val secondKravStatus = inMapList.last().values.last().status
        val firstHttpStatus = inMapList.first().values.first().response.status
        val secondHttpStatus = inMapList.last().values.last().response.status

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
                inMapList.first().keys.first() to inMapList.first().values.first().copy(status = newStatus),
                inMapList.last().keys.last() to inMapList.last().values.last().copy(status = newStatus)
            )
        )
    }

    private suspend fun sendEndreKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravTable,
    ): Map<String, RequestResult> {

        return if (krav.kravtype == ENDRE_RENTER) {
            val endreRenterRequest = makeEndreRenteRequest(krav)
            val endreRenterResponse =
                skeClient.endreRenter(endreRenterRequest, kravIdentifikator, kravIdentifikatorType, krav.corr_id)

            val requestResultEndreRente = RequestResult(
                response = endreRenterResponse,
                request = Json.encodeToString(endreRenterRequest),
                krav = krav,
                kravIdentifikator = "",
                corrId = krav.corr_id,
                status = defineStatus(endreRenterResponse)
            )

            mapOf(ENDRE_RENTER to requestResultEndreRente)

        } else {
            val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
            val endreHovedstolResponse =
                skeClient.endreHovedstol(endreHovedstolRequest, kravIdentifikator, kravIdentifikatorType, krav.corr_id)

            val requestResultEndreHovedstol = RequestResult(
                response = endreHovedstolResponse,
                request = Json.encodeToString(endreHovedstolRequest),
                krav = krav,
                kravIdentifikator = "",
                corrId = krav.corr_id,
                status = defineStatus(endreHovedstolResponse)
            )

            mapOf(ENDRE_HOVEDSTOL to requestResultEndreHovedstol)

        }
    }
}