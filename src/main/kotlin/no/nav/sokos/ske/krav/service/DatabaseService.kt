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
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository.deleteOldFeilmeldinger
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.deleteOldFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertFileValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertLineFilValideringsfeil
import no.nav.sokos.ske.krav.repository.KravRepository.deleteOldKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForAvstemming
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForResending
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForStatusCheck
import no.nav.sokos.ske.krav.repository.KravRepository.getAllUnsentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getPreviousReferansenummer
import no.nav.sokos.ske.krav.repository.KravRepository.getSkeKravidentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.insertAllNewKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateEndringWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.updateSentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatusForAvstemtKravToReported
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.RequestResult

private val logger = KotlinLogging.logger {}

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun getSkeKravidentifikator(navref: String): String =
        dataSource.connection.useAndHandleErrors {
            it.getSkeKravidentifikator(navref).ifBlank {
                val kravId2 = it.getPreviousReferansenummer(navref)
                if (kravId2.isNotBlank()) it.getSkeKravidentifikator(kravId2) else ""
            }
        }

    private fun updateSentKrav(
        skeKravidentifikator: String,
        corrID: String,
        responseStatus: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.updateSentKrav(corrID, skeKravidentifikator, responseStatus)
    }

    private fun updateSentKrav(
        corrID: String,
        responseStatus: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.updateSentKrav(corrID, responseStatus)
    }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.insertAllNewKrav(kravLinjer, filnavn)
    }

    fun saveLineValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.insertLineFilValideringsfeil(filnavn, kravlinje, feilmelding)
    }

    fun saveFileValidationError(
        filnavn: String,
        feilmelding: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.insertFileValideringsfeil(filnavn, feilmelding)
    }

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

    fun getAllKravForStatusCheck(): List<Krav> = dataSource.connection.useAndHandleErrors { it.getAllKravForStatusCheck() }

    fun getAllKravForAvstemming(): List<Krav> = dataSource.connection.useAndHandleErrors { it.getAllKravForAvstemming() }

    fun getFileValidationMessage(filNavn: String): List<FilValideringsfeil> = dataSource.connection.useAndHandleErrors { it.getFilValideringsFeilForFil(filNavn) }

    fun updateStatus(
        mottakStatus: String,
        corrId: String,
    ) = dataSource.connection.useAndHandleErrors { it.updateStatus(mottakStatus, corrId) }

    fun updateStatusForAvstemtKravToReported(kravId: Int) = dataSource.connection.useAndHandleErrors { it.updateStatusForAvstemtKravToReported(kravId) }

    fun getAllKravForResending(): List<Krav> = dataSource.connection.useAndHandleErrors { it.getAllKravForResending() }

    fun getAllUnsentKrav(): List<Krav> = dataSource.connection.useAndHandleErrors { it.getAllUnsentKrav() }

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.updateEndringWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)
    }

    fun deleteOldData() {
        val threshold = LocalDate.now().minusYears(10)
        dataSource.connection.useAndHandleErrors {
            val kravDeleted = it.deleteOldKrav(threshold)
            val filValideringsfeilDeleted = it.deleteOldFilValideringsfeil(threshold)

            logger.info { "Slettet $kravDeleted krav og $filValideringsfeilDeleted filvalideringsfeil" }
        }

        dataSource.transaction { session ->
            val feilmeldingDeleted = deleteOldFeilmeldinger(session, threshold)

            logger.info { "Slettet $feilmeldingDeleted feilmeldinger." }
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
