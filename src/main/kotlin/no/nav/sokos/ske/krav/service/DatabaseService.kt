package no.nav.sokos.ske.krav.service

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.RequestResult

private val logger = KotlinLogging.logger {}

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val filValideringsFeilRepository: FilValideringsfeilRepository = FilValideringsfeilRepository.instance,
    private val feilmeldingRepository: FeilmeldingRepository = FeilmeldingRepository.instance,
    private val kravRepository: KravRepository = KravRepository.instance,
) {
    fun getSkeKravidentifikator(navref: String): String =
        kravRepository.getSkeKravidentifikator(navref).ifBlank {
            val kravId2 = kravRepository.getPreviousReferansenummer(navref)
            if (kravId2.isNotBlank()) kravRepository.getSkeKravidentifikator(kravId2) else ""
        }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) {
        kravRepository.insertAllNewKrav(kravLinjer, filnavn)
    }

    fun saveLineValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        filValideringsFeilRepository.insertLineFilValideringsfeil(filnavn, kravlinje, feilmelding)
    }

    fun saveFileValidationError(
        filnavn: String,
        feilmelding: String,
    ) {
        filValideringsFeilRepository.insertFilValideringsfeil(filnavn, feilmelding)
    }

    fun updateSentKrav(results: List<RequestResult>) {
        incrementMetrics(results)
        results.forEach { result ->
            Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)

            val skeKravidentifikator = if (result.krav.kravtype == NYTT_KRAV) result.kravidentifikator else null
            kravRepository.updateSentKrav(result.krav.corrId, result.status, skeKravidentifikator)
        }
    }

    fun getAllKravForStatusCheck(): List<Krav> = kravRepository.getAllKravForStatusCheck()

    fun getAllKravForAvstemming(): List<Krav> = kravRepository.getAllKravForAvstemming()

    fun getFileValidationMessage(filNavn: String): List<FilValideringsfeil> = filValideringsFeilRepository.getFilValideringsFeilForFil(filNavn)

    fun updateStatus(
        mottakStatus: Status,
        corrId: String,
    ) {
        kravRepository.updateStatus(mottakStatus, corrId)
    }

    fun updateStatusForAvstemtKravToReported(kravId: Int) = feilmeldingRepository.updateStatusForAvstemtKravToReported(kravId)

    fun getAllKravForResending(): List<Krav> = kravRepository.getAllKravForResending()

    fun getAllUnsentKrav(): List<Krav> = kravRepository.getAllUnsentKrav()

    fun getAllUnsentEndringerAndStopp(): List<Krav> = kravRepository.getAllUnsentEndringerAndStopp()

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = kravRepository.updateEndringWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)

    fun deleteOldData() {
        val threshold = LocalDate.now().minusYears(10)

        filValideringsFeilRepository.deleteOldFilValideringsfeil(threshold).takeIf { it != 0 }?.let {
            logger.info { "Slettet $it filvalideringsfeil." }
        }

        feilmeldingRepository.deleteOldFeilmeldinger(threshold).takeIf { it != 0 }?.let {
            logger.info { "Slettet $it feilmeldinger." }
        }

        kravRepository.deleteOldKrav(threshold).takeIf { it != 0 }?.let {
            logger.info { "Slettet $it krav." }
        }
    }

    private fun incrementMetrics(results: List<RequestResult>) {
        Metrics.numberOfKravSent.increment(results.size.toDouble())
        Metrics.numberOfKravFeilet.increment(results.filter { !it.httpStatusCode.isSuccess() }.size.toDouble())
        Metrics.numberOfNyeKrav.increment(results.filter { it.krav.kravtype == NYTT_KRAV }.size.toDouble())
        Metrics.numberOfEndringerAvKrav.increment(results.filter { it.krav.kravtype == ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }.size.toDouble())
        Metrics.numberOfStoppAvKrav.increment(results.filter { it.krav.kravtype == STOPP_KRAV }.size.toDouble())
    }
}
