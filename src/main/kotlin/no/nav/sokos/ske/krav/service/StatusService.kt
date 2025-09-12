package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotliquery.Session
import mu.KotlinLogging

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.ske.responses.MottaksStatusResponse
import no.nav.sokos.ske.krav.dto.ske.responses.ValideringsFeilResponse
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.util.createKravidentifikatorPair
import no.nav.sokos.ske.krav.util.parseTo

private val logger = KotlinLogging.logger {}

class StatusService(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val kravRepository: KravRepository = KravRepository(dataSource),
    private val feilmeldingRepository: FeilmeldingRepository = FeilmeldingRepository(dataSource),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = kravRepository.getAllKravForStatusCheck()
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
            response.parseTo<MottaksStatusResponse>()?.also { mottaksStatusResponse ->
                updateMottaksStatus(
                    mottaksstatus = mottaksStatusResponse,
                    kravIdentifikatorPair = kravidentifikator to kravidentifikatorType,
                    krav = krav,
                )
            }
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
    ) {
        dataSource.transaction { session ->
            kravRepository.updateStatus(mottaksstatus.mottaksStatus, krav.corrId, session).also {
                if (mottaksstatus.mottaksStatus == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value) {
                    handleValideringsFeil(kravIdentifikatorPair, krav, session)
                }
            }
        }
    }

    private suspend fun handleValideringsFeil(
        kravIdentifikatorPair: Pair<String, KravidentifikatorType>,
        krav: Krav,
        session: Session,
    ) {
        val response = skeClient.getValideringsfeil(kravIdentifikatorPair.first, kravIdentifikatorPair.second)
        if (!response.status.isSuccess()) {
            handleFailedStatusResponse(response, krav, "Feil i henting av valideringsfeil", "getValideringsfeil")
            return
        }

        val valideringsfeil = response.parseTo<ValideringsFeilResponse>()?.valideringsfeil ?: return
        logger.error("Asynk Valideringsfeil mottatt: ${valideringsfeil.joinToString { it.error }} ")

        feilmeldingRepository.insertFeilmeldinger(
            feilmeldinger =
                valideringsfeil.map { feil ->
                    slackService.addError(krav.filnavn, "Asynk valideringsfeil", Pair(feil.error, feil.message))

                    Feilmelding(
                        kravId = krav.kravId,
                        corrId = krav.corrId,
                        saksnummerNav = krav.saksnummerNAV,
                        kravidentifikatorSKE = krav.kravidentifikatorSKE,
                        error = feil.error,
                        melding = feil.message,
                        navRequest = "",
                        skeResponse = "",
                        tidspunktOpprettet = LocalDateTime.now(),
                    )
                },
            session,
        )
    }
}
