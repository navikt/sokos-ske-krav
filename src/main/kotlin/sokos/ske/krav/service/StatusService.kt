package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeil
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.util.createKravidentifikatorPair
import java.time.LocalDateTime


class StatusService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    private val logger = KotlinLogging.logger {}
    suspend fun hentOgOppdaterMottaksStatus() {
        var antall = 0
        var feil = 0

        val start = Clock.System.now()
        val krav = databaseService.hentAlleKravSomIkkeErReskotrofort()
        println("antall krav som ikke er reskontroført: ${krav.size}")
        var tidSiste = Clock.System.now()
        val tidHentAlleKrav = (tidSiste - start).inWholeMilliseconds
        var tidHentMottakstatus = 0L
        var tidOppdaterstatus = 0L

        krav.forEach {

            val kravIdentifikatorPair = createKravidentifikatorPair(it)
            antall++
            val response = skeClient.getMottaksStatus(kravIdentifikatorPair.first, kravIdentifikatorPair.second)

            tidHentMottakstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
            tidSiste = Clock.System.now()

            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus.mottaksStatus, it.corr_id)
                    tidOppdaterstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
                    tidSiste = Clock.System.now()
                    if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
                        hentOgLagreValideringsFeil(kravIdentifikatorPair, it)
                } catch (e: SerializationException) {
                    feil++
                    logger.error("Feil i dekoding av MottaksStatusResponse: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    feil++
                    logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}")
                }
            } else {
                logger.info { "Kall til mottaksstatus hos skatt feilet: ${response.status.value}, ${response.status.description}" }
            }

        }
        println("Antall krav hele greia: Antall behandlet  $antall, Antall feilet: $feil")
        println("tid for hele greia: ${(Clock.System.now() - start).inWholeMilliseconds}")
        println("Tid for å hente alle krav: $tidHentAlleKrav")
        println("Totalt tid for Henting av MOTTAKSTATUS: $tidHentMottakstatus")
        println("Totalt tid for Oppdatering av MOTTAKSTATUS: $tidOppdaterstatus")
    }

    private suspend fun hentOgLagreValideringsFeil(
        kravIdentifikatorPair: Pair<String, Kravidentifikatortype>,
        kravTable: KravTable
    ): Map<KravTable, List<ValideringsFeil>> {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        if (response.status.isSuccess()) {
            val valideringsfeil = response.body<ValideringsFeilResponse>().valideringsfeil
            valideringsfeil.forEach {
                val feilmeldingTable = FeilmeldingTable(
                    0,
                    kravTable.kravId,
                    kravTable.corr_id,
                    kravTable.saksnummerNAV,
                    kravTable.saksnummerSKE,
                    it.error,
                    it.message,
                    "",
                    "",
                    LocalDateTime.now()
                )
                databaseService.saveFeilmelding(feilmeldingTable, kravTable.corr_id)
            }
            mapOf(kravTable to valideringsfeil)
        }
        logger.error { "Kall til henting av valideringsfeil hos SKE feilet: ${response.status.value}, ${response.status.description}" }
        return emptyMap()
    }

   fun hentValideringsfeil() = databaseService.getAllFeilmeldinger()

}