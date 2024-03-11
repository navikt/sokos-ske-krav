package sokos.ske.krav.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.makeStoppKravRequest

class StoppKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {

    suspend fun sendAllStopKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {
        val resultMap = kravList.map {
            mapOf(STOPP_KRAV to sendStoppKrav(it))
        }
        databaseService.updateSentKravToDatabase(resultMap)
        return resultMap
    }

    private suspend fun sendStoppKrav(
        krav: KravTable
    ): RequestResult {
        val kravidentifikatorPair = createKravidentifikatorPair(krav)
        val request = makeStoppKravRequest(kravidentifikatorPair.first, kravidentifikatorPair.second)
        val response = skeClient.stoppKrav(request, krav.corr_id)

        val requestResult = RequestResult(
            response = response,
            request = Json.encodeToString(request),
            krav = krav,
            kravIdentifikator = kravidentifikatorPair.first,
            corrId = krav.corr_id,
            status = defineStatus(response)
        )

        return requestResult
    }
}