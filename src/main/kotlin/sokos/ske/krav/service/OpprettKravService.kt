package sokos.ske.krav.service

import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.createOpprettKravRequest
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.parseTo

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    suspend fun sendAllOpprettKrav(kravList: List<KravTable>): List<RequestResult> =
        kravList
            .map { sendOpprettKrav(it) }
            .also { databaseService.updateSentKrav(it) }

    private suspend fun sendOpprettKrav(krav: KravTable): RequestResult {
        val opprettKravRequest = createOpprettKravRequest(krav)
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)
        val kravIdentifikator =
            if (response.status.isSuccess()) {
                response.parseTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: ""
            } else {
                ""
            }
        return RequestResult(
            response = response,
            request = Json.encodeToString(opprettKravRequest),
            kravTable = krav,
            kravidentifikator = kravIdentifikator,
            status = defineStatus(response),
        )
    }
}
