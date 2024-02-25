package sokos.ske.krav.service

import io.ktor.http.*
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
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllStopKrav(kravList: List<KravTable>): List<Map<String, RequestResult>> {
        val resultMap = kravList.map {
            mapOf(STOPP_KRAV to sendStoppKrav(it))
        }
        databaseService.updateSentKravToDatabase(resultMap)
        return resultMap
    }

    suspend fun resendStoppKrav(kravList: List<KravTable>) {

        kravList.map {
            val stopResult = sendStoppKrav(it)
            if (stopResult.response.status.isSuccess()) stopResult
            else{

            }
        //      Sjekk at resultatet er ok
        //      hvis resultat ikke ok
        //          finn ut hva som feilet og hva som må gjøres
        //          hvis den skal varsles lagre i varselliste
        //      lagre resultatet sammem med alle de andre
        // for hver slutt
        }

        //Er det innslag i varselliste
        //      Send varsel

        //TODO Vurder om sjekk på og separering til varsel liste skal være felles for alle typer
        //TODO Mao om den skal over i skeService metoden som kaller denne?
        //TODO fordel: bare ett sted, Ulempe: vanskeligere dersom forskjellige regler.

        //returner resultatene for lagring
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