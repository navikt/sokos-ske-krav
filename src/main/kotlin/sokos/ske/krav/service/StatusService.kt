package sokos.ske.krav.service

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackService
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
    private val slackService: SlackService = SlackService(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = databaseService.getAllKravForStatusCheck()
        if (kravListe.isEmpty()) return

        secureLogger.info("Sjekk av mottaksstatus -> Antall krav som ikke er reskontroført: ${kravListe.size}")
        secureLogger.info("Oppdaterer status")
        val updated =
            kravListe.mapNotNull { krav ->
                processKravStatus(krav)?.takeIf { it.mottaksStatus == "RESKONTROFOERT" }?.let { Pair(krav.status, it.mottaksStatus) }
            }

        secureLogger.info { "Antall reskontroførte krav: ${updated.size}" }
        slackService.sendErrors()
    }

    private suspend fun processKravStatus(krav: KravTable): MottaksStatusResponse? {
        val (kravidentifikator, kravidentifikatorType) = createKravidentifikatorPair(krav)
        val response = skeClient.getMottaksStatus(kravidentifikator, kravidentifikatorType)

        return if (response.status.isSuccess()) {
            response.parseTo<MottaksStatusResponse>()?.also { updateMottaksStatus(it, kravidentifikator to kravidentifikatorType, krav) }
        } else {
            handleFailedStatusResponse(response, krav, "Feil i oppdatering av mottaksstatus", "getMottaksStatus")
            null
        }
    }

    private suspend fun handleFailedStatusResponse(
        response: HttpResponse,
        krav: KravTable,
        feilmeldingHeader: String,
        funksjonsKall: String,
    ) {
        val feilmelding = response.parseTo<FeilResponse>()
        if (feilmelding != null) {
            slackService.addError(
                fileName = krav.filnavn,
                header = feilmeldingHeader,
                Pair(feilmelding.title, feilmelding.detail),
            )
            secureLogger.error { "$funksjonsKall feilet: ${feilmelding.title}" }
        } else {
            val responseBody = response.bodyAsText()
            secureLogger.error { "$funksjonsKall feilet: ${response.status}: $responseBody" }
        }
    }

    private suspend fun updateMottaksStatus(
        mottaksstatus: MottaksStatusResponse,
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: KravTable,
    ) = databaseService.updateStatus(mottaksstatus.mottaksStatus, krav.corrId).also {
        if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value) handleValideringsFeil(kravIdentifikatorPair, krav)
    }

    private suspend fun handleValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        kravTable: KravTable,
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        if (!response.status.isSuccess()) {
            handleFailedStatusResponse(response, kravTable, "Feil i henting av valideringsfeil", "getValideringsfeil")
            return
        }

        val valideringsfeil = response.parseTo<ValideringsFeilResponse>()?.valideringsfeil ?: return
        secureLogger.error("Asynk Valideringsfeil mottatt: ${valideringsfeil.joinToString { it.error }} ")

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

            slackService.addError(kravTable.filnavn, "Asynk valideringsfeil", Pair(it.error, it.message))
        }
    }
}
