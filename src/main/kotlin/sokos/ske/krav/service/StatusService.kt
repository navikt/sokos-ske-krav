package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.util.createKravidentifikatorPair
import java.time.LocalDateTime

class StatusService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService
) {
    private val logger = KotlinLogging.logger("secureLogger")

    suspend fun hentOgOppdaterMottaksStatus() {
        val krav = databaseService.getAllKravForStatusCheck()
        logger.info("antall krav som ikke er reskontroført: ${krav.size}")

        krav.forEach {
            val kravIdentifikatorPair = createKravidentifikatorPair(it)
            val response = skeClient.getMottaksStatus(kravIdentifikatorPair.first, kravIdentifikatorPair.second)

            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus.mottaksStatus, it.corr_id)
                    if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
                        hentOgLagreValideringsFeil(kravIdentifikatorPair, it)
                } catch (e: SerializationException) {
                    logger.error("Feil i dekoding av MottaksStatusResponse: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}")
                }
            } else {
                logger.error { "Kall til mottaksstatus hos skatt feilet: ${response.status.value}, ${response.status.description}" }
            }
        }
    }

    private suspend fun hentOgLagreValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        kravTable: KravTable
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        if (response.status.isSuccess()) {
            val valideringsfeil = response.body<ValideringsFeilResponse>().valideringsfeil
            valideringsfeil.forEach {
                val feilmeldingTable = FeilmeldingTable(
                    0,
                    kravTable.kravId,
                    kravTable.corr_id,
                    kravTable.saksnummerNAV,
                    kravTable.kravidentifikatorSKE,
                    it.error,
                    it.message,
                    "",
                    "",
                    LocalDateTime.now()
                )
                databaseService.saveFeilmelding(feilmeldingTable)
            }
        } else {
            logger.error { "Kall til henting av valideringsfeil hos SKE feilet: ${response.status.value}, ${response.status.description}" }
        }
    }
    fun hentValideringsfeil() = databaseService.getAllFeilmeldinger()
}