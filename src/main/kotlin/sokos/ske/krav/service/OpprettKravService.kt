package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.createOpprettKravRequest
import sokos.ske.krav.util.defineStatus

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    private val logger = KotlinLogging.logger("secureLogger")

    suspend fun sendAllOpprettKrav(kravList: List<KravTable>): List<RequestResult> {
        val responseList =
            kravList.map {
                sendOpprettKrav(it)
            }
        logger.info("Krav sendt lagrer i db")
        databaseService.updateSentKrav(responseList)
        logger.info("Lagret i db returnerer responseList")
        return responseList
    }

    private suspend fun sendOpprettKrav(krav: KravTable): RequestResult {
        val opprettKravRequest = createOpprettKravRequest(krav)
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)

        val kravIdentifikator =
            if (response.status.isSuccess()) {
                response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
            } else {
                ""
            }

        logger.info( "Krav sendt, lager requestResult")
        return RequestResult(
            response = response,
            request = Json.encodeToString(opprettKravRequest),
            kravTable = krav,
            kravidentifikator = kravIdentifikator,
            status = defineStatus(response)
        )
    }
}
