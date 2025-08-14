package no.nav.sokos.ske.krav.service

import mu.KotlinLogging

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.util.FailedRequestResult
import no.nav.sokos.ske.krav.util.HttpResult
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString
import no.nav.sokos.ske.krav.util.parseTo

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun sendAllOpprettKrav(kravList: List<KravTable>): List<RequestResult> =
        kravList
            .mapNotNull {
                when (val result: HttpResult = sendOpprettKrav(it)) {
                    is HttpResult.Success -> {
                        result.result
                    }
                    is HttpResult.Failed -> {
                        // oppdatere status i database til feil elns
                        null
                    }
                }
            }.also { databaseService.updateSentKrav(it) }

    private suspend fun sendOpprettKrav(krav: KravTable): HttpResult {
        val opprettKravRequest = createOpprettKravRequest(krav)
        return try {
            val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)
            HttpResult.Success(
                RequestResult(
                    response = response,
                    request = opprettKravRequest.encodeToString(),
                    kravTable = krav,
                    kravidentifikator = response.parseTo<OpprettInnkrevingsOppdragResponse>()?.kravidentifikator ?: "",
                    status = defineStatus(response),
                ),
            )
        } catch (e: Exception) {
            logger.error("Feil i innsending av nytt krav: ", e)
            HttpResult.Failed(
                FailedRequestResult(
                    opprettKravRequest.encodeToString(),
                    krav,
                    Status.EXCEPTION_I_INNSENDING_AV_NYTT_KRAV,
                ),
            )
        }
    }
}
