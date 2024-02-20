package sokos.ske.krav.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.makeStoppKravRequest

class StoppKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    suspend fun sendAllStopKrav(kravList: List<KravLinje>) =
        kravList.map {
            val skeKravident = databaseService.getSkeKravident(it.referanseNummerGammelSak)
            val kravidentifikatorPair = createKravidentifikatorPair(it, skeKravident)
            mapOf(STOPP_KRAV to sendStoppKrav(kravidentifikatorPair.first, kravidentifikatorPair.second, it))
        }.flatMap { it.entries }.associate { it.key to it.value }

    suspend fun sendStoppKrav(
        kravIdentifikator: String,
        kravIdentifikatorType: Kravidentifikatortype,
        krav: KravLinje
    ): SkeService.RequestResult {
        val request = makeStoppKravRequest(kravIdentifikator, kravIdentifikatorType)
        val response = skeClient.stoppKrav(request, krav.corrId)

        val requestResult = SkeService.RequestResult(
            response = response,
            request = Json.encodeToString(request),
            krav = krav,
            kravIdentifikator = kravIdentifikator,
            corrId = krav.corrId
        )

        return requestResult
    }
}