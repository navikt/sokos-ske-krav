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
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository.deleteOldFeilmeldinger
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.deleteOldFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertLineFilValideringsfeil
import no.nav.sokos.ske.krav.repository.KravRepository.deleteOldKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForAvstemming
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForResending
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForStatusCheck
import no.nav.sokos.ske.krav.repository.KravRepository.getAllUnsentEndringerAndStopp
import no.nav.sokos.ske.krav.repository.KravRepository.getAllUnsentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getPreviousReferansenummer
import no.nav.sokos.ske.krav.repository.KravRepository.getSkeKravidentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.insertAllNewKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateEndringWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.updateSentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatusForAvstemtKravToReported
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.RequestResult

private val logger = KotlinLogging.logger {}

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun getSkeKravidentifikator(navref: String): String =
        dataSource.transaction {
            getSkeKravidentifikator(it, navref).ifBlank {
                val kravId2 = getPreviousReferansenummer(it, navref)
                if (kravId2.isNotBlank()) getSkeKravidentifikator(it, kravId2) else ""
            }
        }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) {
        dataSource.transaction {
            insertAllNewKrav(it, kravLinjer, filnavn)
        }
    }

    fun saveLineValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        dataSource.transaction { tx ->
            insertLineFilValideringsfeil(tx, filnavn, kravlinje, feilmelding)
        }
    }

    fun saveFileValidationError(
        filnavn: String,
        feilmelding: String,
    ) {
        dataSource.transaction { tx ->
            insertFilValideringsfeil(tx, filnavn, feilmelding)
        }
    }

    fun updateSentKrav(results: List<RequestResult>) {
        incrementMetrics(results)
        results.forEach { result ->
            Metrics.incrementKravKodeSendtMetric(result.krav.kravkode)

            val skeKravidentifikator = if (result.krav.kravtype == NYTT_KRAV) result.kravidentifikator else null
            dataSource.transaction { tx ->
                updateSentKrav(tx, result.krav.corrId, result.status, skeKravidentifikator)
            }
        }
    }

    fun getAllKravForStatusCheck(): List<Krav> = dataSource.transaction { getAllKravForStatusCheck(it) }

    fun getAllKravForAvstemming(): List<Krav> = dataSource.transaction { getAllKravForAvstemming(it) }

    fun getFileValidationMessage(filNavn: String): List<FilValideringsfeil> = dataSource.transaction { getFilValideringsFeilForFil(it, filNavn) }

    fun updateStatus(
        mottakStatus: Status,
        corrId: String,
    ) {
        dataSource.transaction { tx -> updateStatus(tx, mottakStatus, corrId) }
    }

    fun updateStatusForAvstemtKravToReported(kravId: Int) = dataSource.transaction { updateStatusForAvstemtKravToReported(it, kravId) }

    fun getAllKravForResending(): List<Krav> = dataSource.transaction { getAllKravForResending(it) }

    fun getAllUnsentKrav(): List<Krav> = dataSource.transaction { getAllUnsentKrav(it) }

    fun getAllUnsentEndringerAndStopp(): List<Krav> = dataSource.transaction { getAllUnsentEndringerAndStopp(it) }

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = dataSource.transaction {
        updateEndringWithSkeKravIdentifikator(it, navsaksnummer, skeKravidentifikator)
    }

    fun deleteOldData() {
        val threshold = LocalDate.now().minusYears(10)

        dataSource.transaction { tx ->
            deleteOldKrav(tx, threshold).takeIf { it != 0 }?.let {
                logger.info { "Slettet $it krav." }
            }

            deleteOldFilValideringsfeil(tx, threshold).takeIf { it != 0 }?.let {
                logger.info { "Slettet $it filvalideringsfeil." }
            }

            deleteOldFeilmeldinger(tx, threshold).takeIf { it != 0 }?.let {
                logger.info { "Slettet $it feilmeldinger." }
            }
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
