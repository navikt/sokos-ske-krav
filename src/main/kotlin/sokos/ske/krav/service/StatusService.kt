package sokos.ske.krav.service

import io.ktor.http.isSuccess
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.util.createKravidentifikatorPair
import sokos.ske.krav.util.parseTo
import java.time.LocalDateTime

class StatusService(
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val slackClient: SlackClient = SlackClient(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = databaseService.getAllKravForStatusCheck()
        if (kravListe.isNotEmpty()) secureLogger.info("Sjekk av mottaksstatus -> Antall krav som ikke er reskontroført: ${kravListe.size}")

        val feil = mutableMapOf<String, MutableList<Pair<String, String>>>()

        val updated =
            kravListe.mapNotNull { krav ->
                val kravIdentifikatorPair = createKravidentifikatorPair(krav)
                val response = skeClient.getMottaksStatus(kravIdentifikatorPair.first, kravIdentifikatorPair.second)

                if (response.status.isSuccess()) {
                    response.parseTo<MottaksStatusResponse>()?.let { status ->
                        updateMottaksStatus(status, kravIdentifikatorPair, krav)
                        if (status.mottaksStatus == "RESKONTROFOERT") Pair(krav.status, status.mottaksStatus) else null
                    }
                } else {
                    response.parseTo<FeilResponse>()?.let { feilmelding ->
                        secureLogger.error { "getMottaksStatus feilet: ${feilmelding.title}" }
                        val errorPair = Pair(feilmelding.title, feilmelding.detail)
                        feil.putIfAbsent(krav.filnavn, mutableListOf(errorPair))?.add(errorPair)
                    }
                    null
                }
            }
        if (updated.isNotEmpty()) secureLogger.info { "Antall reskontroførte krav: ${updated.size}" }
        feil.forEach { (fileName, messages) ->
            slackClient.sendMessage("Feil i oppdatering av mottaksstatus", fileName, messages)
        }
    }

    private suspend fun updateMottaksStatus(
        mottaksstatus: MottaksStatusResponse,
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: KravTable,
    ) {
        databaseService.updateStatus(mottaksstatus.mottaksStatus, krav.corrId)

        if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value) {
            handleValideringsFeil(kravIdentifikatorPair, krav)
        }
    }

    private suspend fun handleValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        kravTable: KravTable,
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)

        if (!response.status.isSuccess()) {
            response.parseTo<FeilResponse>()?.let { feilmelding ->
                secureLogger.error { "getValideringsfeil feilet: ${feilmelding.title}" }
                slackClient.sendMessage("Feil i henting av valideringsfeil", "${kravTable.filnavn}: Saksnummer ${kravTable.saksnummerNAV}", Pair(feilmelding.title, feilmelding.detail))
            }
            return
        }

        val valideringsfeil = response.parseTo<ValideringsFeilResponse>()?.valideringsfeil ?: emptyList()
        if (valideringsfeil.isEmpty()) return

        secureLogger.info("Asynk Valideringsfeil mottatt: ${valideringsfeil.joinToString { it.error }} ")

        valideringsfeil.forEach {
            databaseService.saveFeilmelding(
                FeilmeldingTable(
                    0,
                    kravTable.kravId,
                    kravTable.corrId,
                    kravTable.saksnummerNAV,
                    kravTable.kravidentifikatorSKE,
                    it.error,
                    it.message,
                    "",
                    "",
                    LocalDateTime.now(),
                ),
            )

            slackClient.sendMessage("Asynk valideringsfeil", "${kravTable.filnavn}: Saksnummer ${kravTable.saksnummerNAV}", Pair(it.error, it.message))
        }
    }
}
