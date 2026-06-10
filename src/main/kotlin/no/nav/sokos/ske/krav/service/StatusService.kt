package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime
import javax.sql.DataSource

import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.ske.responses.MottaksStatusResponse
import no.nav.sokos.ske.krav.dto.ske.responses.ValideringsFeilResponse
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.decodeTo
import no.nav.sokos.ske.krav.util.transaction

private val logger = mu.KotlinLogging.logger {}

// TODO: Burde renames til MottaksstatusService? Trenger kanskje en refaktorering
class StatusService(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val slackService: SlackService = SlackService(),
    private val feilmeldingRepository: FeilmeldingRepository = FeilmeldingRepository.instance,
    private val kravRepository: KravRepository = KravRepository.instance,
) {
    suspend fun getMottaksStatus() {
        val kravListe = kravRepository.getAllKravForStatusCheck()
        if (kravListe.isEmpty()) return

        logger.info("Sjekk av mottaksstatus -> Antall krav som ikke er reskontroført: ${kravListe.size}")
        logger.info("Oppdaterer status")

        var reskontrofoerteKravCount = 0
        for (krav in kravListe) {
            runCatching {
                val mottaksStatusResponse = processKravStatus(krav)
                if (mottaksStatusResponse?.mottaksStatus == Status.RESKONTROFOERT || mottaksStatusResponse?.mottaksStatus == Status.MIGRERT) {
                    reskontrofoerteKravCount++
                }
            }.onFailure { break }
        }

        logger.info { "Antall reskontroførte krav: $reskontrofoerteKravCount" }
        slackService.sendErrors()
    }

    private suspend fun processKravStatus(krav: Krav): MottaksStatusResponse? {
        val (kravidentifikator, kravidentifikatorType) = createKravidentifikatorPair(krav)
        val response = skeClient.getMottaksStatus(kravidentifikator, kravidentifikatorType)
        val responseBody = response.bodyAsText()

        return if (response.status.isSuccess()) {
            responseBody.decodeTo<MottaksStatusResponse>()?.also { updateMottaksStatus(it, kravidentifikator to kravidentifikatorType, krav) }
        } else {
            handleFailedStatusResponse(responseBody, krav, "Feil i oppdatering av mottaksstatus", "getMottaksStatus")
            null
        }
    }

    private fun handleFailedStatusResponse(
        responseBody: String,
        krav: Krav,
        feilmeldingHeader: String,
        funksjonsKall: String,
    ) {
        val feilmelding = responseBody.decodeTo<FeilResponse>()
        if (feilmelding != null) {
            slackService.addError(
                fileName = krav.filnavn,
                header = feilmeldingHeader,
                Pair(feilmelding.title, feilmelding.detail),
                krav.saksnummerNAV,
            )
            logger.error { "$funksjonsKall feilet: ${feilmelding.title}" }
        }
    }

    private suspend fun updateMottaksStatus(
        mottaksStatusResponse: MottaksStatusResponse,
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
    ) {
        dataSource.transaction { session ->
            kravRepository.updateStatus(session, mottaksStatusResponse.mottaksStatus, krav.corrId)
        }
        if (mottaksStatusResponse.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS) handleValideringsFeil(kravIdentifikatorPair, krav)
    }

    private suspend fun handleValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            handleFailedStatusResponse(responseBody, krav, "Feil i henting av valideringsfeil", "getValideringsfeil")
            return
        }

        val valideringsfeilListe = responseBody.decodeTo<ValideringsFeilResponse>()?.valideringsfeil ?: return
        logger.info("Asynk Valideringsfeil mottatt ")
        valideringsfeilListe.forEach { valideringsFeil ->
            logger.warn { "Asynk valideringsfeil mottatt: ${ valideringsFeil.message }" }
            slackService.addError(krav.filnavn, "Asynk valideringsfeil", Pair(valideringsFeil.error, valideringsFeil.message), krav.saksnummerNAV)
        }

        val feilmeldinger =
            valideringsfeilListe.map { valideringsFeil ->
                Feilmelding(
                    0,
                    krav.kravId,
                    krav.corrId,
                    krav.saksnummerNAV,
                    krav.kravidentifikatorSKE,
                    valideringsFeil.error,
                    valideringsFeil.message,
                    "",
                    "",
                    LocalDateTime.now(),
                )
            }

        dataSource.transaction { session ->
            feilmeldingRepository.insertFeilmeldinger(session, feilmeldinger)
        }
    }
}
