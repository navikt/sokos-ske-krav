package no.nav.sokos.ske.krav.service

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.database.PostgresDataSource
import no.nav.sokos.ske.krav.database.models.FeilmeldingTable
import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.database.models.ValideringsfeilTable
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.getAllFeilmeldinger
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.getFeilmeldingForKravId
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.insertFeilmelding
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForAvstemming
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForResending
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForStatusCheck
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllUnsentKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.getFailedHttpKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.getKravTableIdFromCorrelationId
import no.nav.sokos.ske.krav.database.repository.KravRepository.getPreviousReferansenummer
import no.nav.sokos.ske.krav.database.repository.KravRepository.getSkeKravidentifikator
import no.nav.sokos.ske.krav.database.repository.KravRepository.insertAllNewKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateEndringWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateNyttKravWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateSentKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateStatusForAvstemtKravToReported
import no.nav.sokos.ske.krav.database.repository.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.getValideringsFeilForFil
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.insertFileValideringsfeil
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.insertLineValideringsfeil
import no.nav.sokos.ske.krav.domain.nav.KravLinje
import no.nav.sokos.ske.krav.domain.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.parseTo

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun getFailedHttpKrav() =
        dataSource.connection.useAndHandleErrors { con ->
            con.getFailedHttpKrav()
        }

    fun getSkeKravidentifikator(navref: String): String =
        dataSource.connection.useAndHandleErrors {
            it.getSkeKravidentifikator(navref).ifBlank {
                val kravId2 = it.getPreviousReferansenummer(navref)
                if (kravId2.isNotBlank()) it.getSkeKravidentifikator(kravId2) else ""
            }
        }

    private fun getKravTableIdFromCorrelationId(corrID: String): Long =
        dataSource.connection.useAndHandleErrors {
            it.getKravTableIdFromCorrelationId(corrID)
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

    fun getAllFeilmeldinger(): List<FeilmeldingTable> =
        dataSource.connection.useAndHandleErrors {
            it.getAllFeilmeldinger()
        }

    fun saveFeilmelding(feilMelding: FeilmeldingTable) =
        dataSource.connection.useAndHandleErrors {
            it.insertFeilmelding(feilMelding)
        }

    fun saveLineValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.insertLineValideringsfeil(filnavn, kravlinje, feilmelding)
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
            Metrics.incrementKravKodeSendtMetric(it.kravTable.kravkode)

            if (it.kravTable.kravtype == NYTT_KRAV) {
                updateSentKrav(
                    it.kravidentifikator,
                    it.kravTable.corrId,
                    it.status.value,
                )
            } else {
                updateSentKrav(
                    it.kravTable.corrId,
                    it.status.value,
                )
            }
        }
    }

    suspend fun saveErrorMessage(
        request: String,
        response: HttpResponse?,
        krav: KravTable,
        kravidentifikator: String,
    ) {
        val skeKravidentifikator =
            if (kravidentifikator == krav.saksnummerNAV || kravidentifikator == krav.referansenummerGammelSak) "" else kravidentifikator

        val feilResponse = response?.parseTo<FeilResponse>() ?: return
        val feilmelding =
            FeilmeldingTable(
                0L,
                getKravTableIdFromCorrelationId(krav.corrId),
                krav.corrId,
                krav.saksnummerNAV,
                skeKravidentifikator,
                feilResponse.status.toString(),
                feilResponse.detail,
                request,
                response.bodyAsText(),
                LocalDateTime.now(),
            )

        saveFeilmelding(feilmelding)
    }

    fun getAllKravForStatusCheck(): List<KravTable> = dataSource.connection.useAndHandleErrors { it.getAllKravForStatusCheck() }

    fun getAllKravForAvstemming(): List<KravTable> = dataSource.connection.useAndHandleErrors { it.getAllKravForAvstemming() }

    fun getFeilmeldingForKravId(kravId: Long): List<FeilmeldingTable> = dataSource.connection.useAndHandleErrors { it.getFeilmeldingForKravId(kravId) }

    fun getFileValidationMessage(filNavn: String): List<ValideringsfeilTable> = dataSource.connection.useAndHandleErrors { it.getValideringsFeilForFil(filNavn) }

    fun updateStatus(
        mottakStatus: String,
        corrId: String,
    ) = dataSource.connection.useAndHandleErrors { it.updateStatus(mottakStatus, corrId) }

    fun updateStatusForAvstemtKravToReported(kravId: Int) = dataSource.connection.useAndHandleErrors { it.updateStatusForAvstemtKravToReported(kravId) }

    fun getAllKravForResending(): List<KravTable> = dataSource.connection.useAndHandleErrors { it.getAllKravForResending() }

    fun getAllUnsentKrav(): List<KravTable> = dataSource.connection.useAndHandleErrors { it.getAllUnsentKrav() }

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.updateEndringWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)
    }

    fun updateNyttKravWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) = dataSource.connection.useAndHandleErrors {
        it.updateNyttKravWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)
    }

    private fun incrementMetrics(results: List<RequestResult>) {
        Metrics.numberOfKravSent.increment(results.size.toDouble())
        Metrics.numberOfKravFeilet.increment(results.filter { !it.response.status.isSuccess() }.size.toDouble())
        Metrics.numberOfNyeKrav.increment(results.filter { it.kravTable.kravtype == NYTT_KRAV }.size.toDouble())
        Metrics.numberOfEndringerAvKrav.increment(results.filter { it.kravTable.kravtype == ENDRING_RENTE || it.kravTable.kravtype == ENDRING_HOVEDSTOL }.size.toDouble())
        Metrics.numberOfStoppAvKrav.increment(results.filter { it.kravTable.kravtype == STOPP_KRAV }.size.toDouble())
    }
}
