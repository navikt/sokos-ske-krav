package sokos.ske.krav.service

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllFeilmeldinger
import sokos.ske.krav.database.Repository.getAllKravForAvstemming
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllUniqueKravkoder
import sokos.ske.krav.database.Repository.getAllUnsentKrav
import sokos.ske.krav.database.Repository.getFeilmeldingForKravId
import sokos.ske.krav.database.Repository.getKravTableIdFromCorrelationId
import sokos.ske.krav.database.Repository.getPreviousReferansenummer
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.getValideringsFeilForKravId
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertFeilmelding
import sokos.ske.krav.database.Repository.insertValideringsfeil
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.Repository.updateSentKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.ValideringsfeilTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.RequestResult
import java.time.LocalDateTime

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource(),
) {
    fun getSkeKravidentifikator(navref: String): String {
        dataSource.connection.useAndHandleErrors { con ->
            val kravId1 = con.getSkeKravidentifikator(navref)
            if (kravId1.isNotBlank()) {
                return kravId1
            } else {
                val kravId2 = con.getPreviousReferansenummer(navref)
                if (kravId2.isNotBlank()) {
                    return con.getSkeKravidentifikator(kravId2)
                } else {
                    return ""
                }
            }
        }
    }

    private fun getKravTableIdFromCorrelationId(corrID: String): Long {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getKravTableIdFromCorrelationId(corrID)
        }
    }

    private fun updateSentKrav(
        skeKravidentifikator: String,
        corrID: String,
        responseStatus: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateSentKrav(corrID, skeKravidentifikator, responseStatus)
        }
    }

    private fun updateSentKrav(
        corrID: String,
        responseStatus: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateSentKrav(corrID, responseStatus)
        }
    }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.insertAllNewKrav(kravLinjer, filnavn)
        }
    }

    fun getAllFeilmeldinger(): List<FeilmeldingTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getAllFeilmeldinger()
        }
    }

    fun saveFeilmelding(feilMelding: FeilmeldingTable) {
        dataSource.connection.useAndHandleErrors { con ->
            con.insertFeilmelding(feilMelding)
        }
    }

    fun saveValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.insertValideringsfeil(filnavn, kravlinje, feilmelding)
        }
    }

    fun updateSentKrav(responses: List<RequestResult>) {
        responses.forEach {
            Metrics.numberOfKravSent.increment()
            Metrics.registerTypeKravSendtMetric(it.kravTable.kravkode).increment()

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
        response: HttpResponse,
        krav: KravTable,
        kravidentifikator: String,
    ) {
        val skeKravidentifikator =
            if (kravidentifikator == krav.saksnummerNAV || kravidentifikator == krav.referansenummerGammelSak) "" else kravidentifikator

        val feilResponse = response.body<FeilResponse>()

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

    fun getAllKravForStatusCheck(): List<KravTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForStatusCheck()
        }
    }

    fun getAllKravForAvstemming(): List<KravTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForAvstemming()
        }
    }

    fun getFeilmeldingForKravId(kravId: Long): List<FeilmeldingTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getFeilmeldingForKravId(kravId)
        }
    }

    fun getValidationMessageForKrav(kravTable: KravTable): List<ValideringsfeilTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getValideringsFeilForKravId(kravTable)
        }
    }

    fun updateStatus(
        mottakStatus: String,
        corrId: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateStatus(mottakStatus, corrId)
        }
    }

    fun updateStatusForAvstemtKravToReported(kravId: Int) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateStatusForAvstemtKravToReported(kravId)
        }
    }

    fun getAllKravForResending(): List<KravTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForResending()
        }
    }

    fun getAllUnsentKrav(): List<KravTable> {
        dataSource.connection.useAndHandleErrors { con ->
            return con.getAllUnsentKrav()
        }
    }

    fun updateEndringWithSkeKravIdentifikator(
        navsaksnummer: String,
        skeKravidentifikator: String,
    ) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateEndringWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)
        }
    }

    fun getKravkoder() = dataSource.connection.useAndHandleErrors { con -> con.getAllUniqueKravkoder() }

    fun getKravkodeCount(): Map<String, Int> {
        val getUniqueKravkoder = dataSource.connection.useAndHandleErrors { con -> con.getAllUniqueKravkoder() }

        println(getUniqueKravkoder)
        return emptyMap()
    }
}
