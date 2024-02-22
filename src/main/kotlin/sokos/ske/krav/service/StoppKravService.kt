package sokos.ske.krav.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.makeStoppKravRequest

class StoppKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllStopKrav(kravList: List<KravTable>) =
        kravList.map {
             val kravidentifikatorPair = createKravidentifikatorPair(it)
          mapOf(STOPP_KRAV to sendStoppKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it))
         }

    suspend fun resendStoppKrav(kravTableList: List<KravTable>) {

        // for hver kravTabellLinje
        //      kall sendkrav med it mappet til kravlinje
        //      Sjekk at resultatet er ok
        //      hvis resultat ikke ok
        //          finn ut hva som feilet og hva som må gjøres
        //          hvis den skal varsles lagre i varselliste
        //      lagre resultatet sammem med alle de andre
        // for hver slutt

        //Er det innslag i varselliste
        //      Send varsel

        //TODO Vurder om sjekk på og separering til varsel liste skal være felles for alle typer
        //TODO Mao om den skal over i skeService metoden som kaller denne?
        //TODO fordel: bare ett sted, Ulempe: vanskeligere dersom forskjellige regler.

        //returner resultatene for lagring
    }


    suspend fun sendStoppKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravTable
    ): SkeService.RequestResult {
        val request = makeStoppKravRequest(kravIdentifikator, kravIdentifikatorType)
        val response = skeClient.stoppKrav(request, krav.corr_id)

        val requestResult = SkeService.RequestResult(
            response = response,
            request = Json.encodeToString(request),
            krav = krav,
            kravIdentifikator = kravIdentifikator,
            corrId = krav.corr_id
        )

        return requestResult
    }
}