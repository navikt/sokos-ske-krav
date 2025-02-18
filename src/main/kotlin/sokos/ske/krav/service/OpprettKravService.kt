package sokos.ske.krav.service

import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.createOpprettKravRequest
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.encodeToString
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
        secureLogger.info("Sender krav til SKE: ${krav.corrId}")
        val opprettKravRequest = createOpprettKravRequest(krav)
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)

        return RequestResult(
            response = response,
            request = opprettKravRequest.encodeToString(),
            kravTable = krav,
            kravidentifikator = response.parseTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: "",
            status = defineStatus(response),
        )
    }
}
