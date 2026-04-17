package no.nav.sokos.ske.krav.service

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.RequestResult

private val logger = KotlinLogging.logger {}

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun getSkeKravidentifikator(navref: String): String =
        dataSource.transaction { tx ->
            KravRepository.getSkeKravidentifikator(tx, navref).ifBlank {
                val previousRef = KravRepository.getPreviousReferansenummer(tx, navref)
                if (previousRef.isNotBlank()) KravRepository.getSkeKravidentifikator(tx, previousRef) else ""
            }
        }

    private fun updateSentKrav(
        corrID: String,
        responseStatus: String,
    ) = dataSource.transaction { tx -> KravRepository.updateSentKrav(tx, corrID, responseStatus) }

    private fun updateSentKrav(
        skeKravidentifikator: String,
        corrID: String,
        responseStatus: String,
    ) = dataSource.transaction { tx -> KravRepository.updateSentKrav(tx, corrID, skeKravidentifikator, responseStatus) }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) = dataSource.transaction { tx -> KravRepository.insertAllNewKrav(tx, kravLinjer, filnavn) }

    fun saveLineValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = dataSource.transaction { tx -> FilValideringsfeilRepository.insertLineFilValideringsfeil(tx, filnavn, kravlinje, feilmelding) }

    fun saveFileValidationError(
        filnavn: String,
        feilmelding: String,
    ) = dataSource.transaction { tx -> FilValideringsfeilRepository.insertFileValideringsfeil(tx, filnavn, feilmelding) }

    fun updateSentKrav(results: List<RequestResult>) {
        incrementMetrics(results)
        results.forEach {
            Metrics.incrementKravKodeSendtMetric(it.krav.kravkode)

            if (it.krav.kravtype == NYTT_KRAV) {
                updateSentKrav(
                    it.kravidentifikator,
                    it.krav.corrId,
                    it.status.value,
                )
            } else {
                updateSentKrav(
                    it.krav.corrId,
                    it.status.value,
                )
            }
        }
    }

    fun getAllKravForStatusCheck(): List<Krav> = dataSource.transaction { tx -> KravRepository.getAllKravForStatusCheck(tx) }

    fun getAllKravForAvstemming(): List<Krav> = dataSource.transaction { tx -> KravRepository.getAllKravForAvstemming(tx) }

    fun getFileValidationMessage(filNavn: String): List<FilValideringsfeil> = dataSource.transaction { tx -> FilValideringsfeilRepository.getFilValideringsFeilForFil(tx, filNavn) }

    fun updateStatus(
        mottakStatus: String,
        corrId: String,
    ) = dataSource.transaction { tx -> KravRepository.updateStatus(tx, mottakStatus, corrId) }

    fun updateStatusForAvstemtKravToReported(kravId: Int) = dataSource.transaction { tx -> KravRepository.updateStatusForAvstemtKravToReported(tx, kravId) }

    fun getAllKravForResending(): List<Krav> = dataSource.transaction { tx -> KravRepository.getAllKravForResending(tx) }

    fun getAllUnsentKrav(): List<Krav> = dataSource.transaction { tx -> KravRepository.getAllUnsentKrav(tx) }

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = dataSource.transaction { tx -> KravRepository.updateEndringWithSkeKravIdentifikator(tx, navsaksnummer, skeKravidentifikator) }

    fun deleteOldData() {
        val threshold = LocalDate.now().minusYears(10)
        dataSource.transaction { tx ->
            val kravDeleted = KravRepository.deleteOldKrav(tx, threshold)
            val filValideringsfeilDeleted = FilValideringsfeilRepository.deleteOldFilValideringsfeil(tx, threshold)
            val feilmeldingDeleted = FeilmeldingRepository.deleteOldFeilmeldinger(tx, threshold)

            logger.info { "Slettet $kravDeleted krav, $filValideringsfeilDeleted filvalideringsfeil og $feilmeldingDeleted feilmeldinger." }
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
