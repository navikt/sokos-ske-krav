package sokos.ske.krav.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.util.*
import java.util.*

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllEndreKrav(kravList: List<KravTable>) :List<Map<String, RequestResult>>
    {
        val resultList = kravList.map {
            //hvis vi har to corrid ta bvare på den ekstra
            ///ellers lag en ny UUID
            val hovedstolCorrId = UUID.randomUUID().toString()

            //val endringsMap = kravList.groupBy{it.saksnummerSKE+it.saksnummerNAV}
            val endringsMap = kravList.groupBy{it.saksnummerSKE+it.saksnummerNAV}

            val skeKravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
                val kravidentifikatorPair = createKravidentifikatorPair(it
                )
                sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it, hovedstolCorrId )
            }.flatten()

        return resultList
    }


    suspend fun resendEndreKrav(kravTableList: List<KravTable>) {

    }


    private suspend fun sendEndreKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravTable,
        hovedstolCorrId: String,
    ): List<Map<String, RequestResult>> {


        if (!krav.saksnummerNAV.equals(krav.referanseNummerGammelSak)) {
            skeClient.endreOppdragsGiversReferanse(
                makeNyOppdragsgiversReferanseRequest(krav),
                kravIdentifikator,
                kravIdentifikatorType,
                UUID.randomUUID().toString()
            )
        }

        val endreRenterRequest = makeEndreRenteRequest(krav)
        val endreRenterResponse = skeClient.endreRenter(endreRenterRequest, kravIdentifikator, kravIdentifikatorType, krav.corr_id)

        val requestResultEndreRente = RequestResult(
            response = endreRenterResponse,
            request = Json.encodeToString(endreRenterRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = krav.corr_id,
            status = defineStatus(endreRenterResponse)
        )

        val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
        val endreHovedstolResponse =
            skeClient.endreHovedstol(endreHovedstolRequest, kravIdentifikator, kravIdentifikatorType, hovedstolCorrId)

        val requestResultEndreHovedstol = RequestResult(
            response = endreHovedstolResponse,
            request = Json.encodeToString(endreHovedstolRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = hovedstolCorrId,
            status = defineStatus(endreHovedstolResponse)
        )
        val maps = listOf(mapOf(ENDRE_RENTER to requestResultEndreRente), mapOf( ENDRE_HOVEDSTOL to requestResultEndreHovedstol))


        return maps
    }


}