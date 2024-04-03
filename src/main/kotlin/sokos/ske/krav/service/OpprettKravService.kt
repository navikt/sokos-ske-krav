package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.makeOpprettKravRequest

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {

    suspend fun sendAllOpprettKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {

        val responseList = kravList.map {
            mapOf(NYTT_KRAV to sendOpprettKrav(it))
        }
        databaseService.updateSentKrav(responseList)

        return responseList
    }

    private suspend fun sendOpprettKrav(krav: KravTable): RequestResult {
        val opprettKravRequest = makeOpprettKravRequest(krav)
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corr_id)

        val kravIdentifikator =
            if (response.status.isSuccess())
                response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
            else ""

        val requestResult = RequestResult(
            response = response,
            request = Json.encodeToString(opprettKravRequest),
            krav = krav,
            kravIdentifikator = kravIdentifikator,
        )

        return requestResult
    }

}