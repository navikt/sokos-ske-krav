package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.util.getFnrListe
import sokos.ske.krav.util.getNewFnr
import sokos.ske.krav.util.makeOpprettKravRequest

class OpprettKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllOpprettKrav(kravList: List<KravLinje>): List<Map<String, SkeService.RequestResult>>  {
        val fnrListe = getFnrListe()
        val fnrIter = fnrListe.listIterator()

        return kravList.map {
            mapOf(NYTT_KRAV to sendOpprettKrav(it, getNewFnr(fnrListe, fnrIter)))
        }
    }


    suspend fun sendOpprettKrav(krav: KravLinje, substfnr: String): SkeService.RequestResult {
        val opprettKravRequest = makeOpprettKravRequest(
            krav.copy(
                gjelderID =
                if (krav.gjelderID.startsWith("00")) krav.gjelderID else substfnr
            ), databaseService.insertNewKobling(krav.saksNummer, krav.corrId)
        )
        val response = skeClient.opprettKrav(opprettKravRequest, krav.corrId)
        val kravIdentifikator =
            if (response.status.isSuccess())
                response.body<OpprettInnkrevingsOppdragResponse>().kravidentifikator
            else ""


        val requestResult = SkeService.RequestResult(
            response = response,
            request = Json.encodeToString(opprettKravRequest),
            krav = krav,
            kravIdentifikator = kravIdentifikator,
            corrId = krav.corrId
        )

        return requestResult
    }

}