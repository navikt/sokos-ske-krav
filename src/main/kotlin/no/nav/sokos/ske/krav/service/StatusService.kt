package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.ske.responses.MottaksStatusResponse
import no.nav.sokos.ske.krav.dto.ske.responses.ValideringsFeilResponse
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.util.DBUtils.asyncTransaction
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.decodeTo

private val logger = mu.KotlinLogging.logger {}

// TODO: Burde renames til MottaksstatusService? Trenger kanskje en refaktorering
class StatusService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = databaseService.getAllKravForStatusCheck()
        if (kravListe.isEmpty()) return

        logger.info("Sjekk av mottaksstatus -> Antall krav som ikke er reskontroført: ${kravListe.size}")
        logger.info("Oppdaterer status")

        var reskontrofoerteKravCount = 0
        for (krav in kravListe) {
            runCatching {
                val mottaksStatusResponse = processKravStatus(krav)
                if (mottaksStatusResponse?.mottaksStatus == Status.RESKONTROFOERT) {
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
            )
            logger.error { "$funksjonsKall feilet: ${feilmelding.title}" }
        }
    }

    private suspend fun updateMottaksStatus(
        mottaksstatus: MottaksStatusResponse,
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
    ) = databaseService.updateStatus(mottaksstatus.mottaksStatus.value, krav.corrId).also {
        if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS) handleValideringsFeil(kravIdentifikatorPair, krav)
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
        valideringsfeilListe.forEach {
            logger.error(marker = TEAM_LOGS_MARKER) { "Asynk valideringsfeil mottatt: ${ it.message }" }
        }

        dataSource.asyncTransaction { session ->
            FeilmeldingRepository.insertFeilmeldinger(
                tx = session,
                feilmeldinger =
                    valideringsfeilListe.map { valideringsFeil ->
                        slackService.addError(krav.filnavn, "Asynk valideringsfeil", Pair(valideringsFeil.error, valideringsFeil.message))

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
                    },
            )
        }
    }
}
