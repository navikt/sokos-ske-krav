package no.nav.sokos.ske.krav.service

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString
import no.nav.sokos.ske.krav.util.parseTo

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

        return RequestResult(
            response = response,
            request = opprettKravRequest.encodeToString(),
            kravTable = krav,
            kravidentifikator = response.parseTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: "",
            status = defineStatus(response),
        )
    }
}
