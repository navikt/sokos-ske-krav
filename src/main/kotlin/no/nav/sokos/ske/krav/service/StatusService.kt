package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.ske.responses.MottaksStatusResponse
import no.nav.sokos.ske.krav.dto.ske.responses.ValideringsFeilResponse
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.parseTo

private val logger = mu.KotlinLogging.logger {}

class StatusService(
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = databaseService.getAllKravForStatusCheck()
        if (kravListe.isEmpty()) return

        logger.info("Sjekk av mottaksstatus -> Antall krav som ikke er reskontroført: ${kravListe.size}")
        logger.info("Oppdaterer status")
        val updated =
            kravListe.mapNotNull { krav ->
                processKravStatus(krav)?.takeIf { it.mottaksStatus == "RESKONTROFOERT" }?.let { Pair(krav.status, it.mottaksStatus) }
            }

        logger.info { "Antall reskontroførte krav: ${updated.size}" }
        slackService.sendErrors()
    }

    private suspend fun processKravStatus(krav: Krav): MottaksStatusResponse? {
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
        krav: Krav,
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
            logger.error { "$funksjonsKall feilet: ${feilmelding.title}" }
        } else {
            val responseBody = response.bodyAsText()
            logger.error { "$funksjonsKall feilet: ${response.status}: $responseBody" }
        }
    }

    private suspend fun updateMottaksStatus(
        mottaksstatus: MottaksStatusResponse,
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
    ) = databaseService.updateStatus(mottaksstatus.mottaksStatus, krav.corrId).also {
        if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value) handleValideringsFeil(kravIdentifikatorPair, krav)
    }

    private suspend fun handleValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        if (!response.status.isSuccess()) {
            handleFailedStatusResponse(response, krav, "Feil i henting av valideringsfeil", "getValideringsfeil")
            return
        }

        val valideringsfeil = response.parseTo<ValideringsFeilResponse>()?.valideringsfeil ?: return
        logger.error("Asynk Valideringsfeil mottatt: ${valideringsfeil.joinToString { it.error }} ")

        valideringsfeil.forEach {
            databaseService.saveFeilmelding(
                Feilmelding(
                    0,
                    krav.kravId,
                    krav.corrId,
                    krav.saksnummerNAV,
                    krav.kravidentifikatorSKE,
                    it.error,
                    it.message,
                    "",
                    "",
                    LocalDateTime.now(),
                ),
            )

            slackService.addError(krav.filnavn, "Asynk valideringsfeil", Pair(it.error, it.message))
        }
    }
}
