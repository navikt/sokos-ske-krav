package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.*

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {

    val byttut = true

    //TODO Fjerne bytte fnr funksjonalitet

    suspend fun sendAllOpprettKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {
        val fnrListe = getFnrListe()
        val fnrIter = fnrListe.listIterator()

        val responseList = kravList.map {
            if (byttut) mapOf(NYTT_KRAV to sendOpprettKrav(it, getNewFnr(fnrListe, fnrIter)))
            else mapOf(NYTT_KRAV to sendOpprettKrav(it, ""))
        }
        databaseService.updateSentKravToDatabase(responseList)

        return responseList
    }

    private suspend fun sendOpprettKrav(krav: KravTable, substfnr: String): RequestResult {
        val opprettKravRequest =
            if (byttut) makeOpprettKravRequest(
                krav.copy(
                    gjelderId =
                    if (krav.gjelderId.startsWith("00")) krav.gjelderId else substfnr
                )
            )
            else makeOpprettKravRequest(krav)
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
            corrId = krav.corr_id,
            status = defineStatus(response)
        )

        return requestResult
    }

}