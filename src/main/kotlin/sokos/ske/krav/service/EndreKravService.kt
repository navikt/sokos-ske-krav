package sokos.ske.krav.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.makeEndreHovedstolRequest
import sokos.ske.krav.util.makeEndreRenteRequest
import sokos.ske.krav.util.makeNyOppdragsgiversReferanseRequest
import java.util.*

class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllEndreKrav(kravList: List<KravLinje>) :List<Map<String, SkeService.RequestResult>>
    {
        val resultList = kravList.map {
            //hvis vi har to corrid ta bvare på den ekstra
            ///ellers lag en ny UUID
            val hovedstolCorrId = UUID.randomUUID().toString()
            val skeKravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
                val kravidentifikatorPair = createKravidentifikatorPair(it, skeKravident)
                sendEndreKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it, hovedstolCorrId )
            }.flatten()

        return resultList
    }


    suspend fun sendEndreKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravLinje,
        hovedstolCorrId: String,
    ): List<Map<String, SkeService.RequestResult>> {


        if (!krav.saksNummer.equals(krav.referanseNummerGammelSak)) {
            skeClient.endreOppdragsGiversReferanse(
                makeNyOppdragsgiversReferanseRequest(krav),
                kravIdentifikator,
                kravIdentifikatorType,
                UUID.randomUUID().toString()
            )
        }

        val endreRenterRequest = makeEndreRenteRequest(krav)
        val endreRenterResponse = skeClient.endreRenter(endreRenterRequest, kravIdentifikator, kravIdentifikatorType, krav.corrId)

        val requestResultEndreRente = SkeService.RequestResult(
            response = endreRenterResponse,
            request = Json.encodeToString(endreRenterRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = krav.corrId
        )

        val endreHovedstolRequest = makeEndreHovedstolRequest(krav)
        val endreHovedstolResponse =
            skeClient.endreHovedstol(endreHovedstolRequest, kravIdentifikator, kravIdentifikatorType, hovedstolCorrId)

        val requestResultEndreHovedstol = SkeService.RequestResult(
            response = endreHovedstolResponse,
            request = Json.encodeToString(endreHovedstolRequest),
            krav = krav,
            kravIdentifikator = "",
            corrId = hovedstolCorrId
        )
        val maps = listOf(mapOf(ENDRE_RENTER to requestResultEndreRente), mapOf( ENDRE_HOVEDSTOL to requestResultEndreHovedstol))


        return maps
    }


}