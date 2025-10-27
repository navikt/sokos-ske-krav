package no.nav.sokos.ske.krav.service

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.createStoppKravRequest
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString

class StoppKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    suspend fun sendAllStoppKrav(kravList: List<Krav>): List<RequestResult> {
        val resultList =
            kravList.map {
                sendStoppKrav(it)
            }
        databaseService.updateSentKrav(resultList)
        return resultList
    }

    private suspend fun sendStoppKrav(krav: Krav): RequestResult {
        val kravidentifikatorPair = createKravidentifikatorPair(krav)
        val request = createStoppKravRequest(kravidentifikatorPair.first, kravidentifikatorPair.second)
        val response = skeClient.stoppKrav(request, krav.corrId)

        return RequestResult(
            response = response,
            request = request.encodeToString(),
            krav = krav,
            kravidentifikator = kravidentifikatorPair.first,
            status = defineStatus(response),
        )
    }
}
