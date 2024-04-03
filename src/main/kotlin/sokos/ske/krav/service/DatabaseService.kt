package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllErrorMessages
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllUnsentKrav
import sokos.ske.krav.database.Repository.getAllKravForAvstemming
import sokos.ske.krav.database.Repository.getErrorMessageForKravId
import sokos.ske.krav.database.Repository.getKravTableIdFromCorrelationId
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertErrorMessage
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.database.Repository.updateSentKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.RequestResult
import java.time.LocalDateTime

class DatabaseService(
    private val postgresDataSource: PostgresDataSource
) {

    fun getSkeKravidentifikator(navref: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getSkeKravidentifikator(navref)
        }
    }

    private fun getKravTableIdFromCorrelationId(corrID: String): Long {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getKravTableIdFromCorrelationId(corrID)
        }
    }

    private fun updateSentKrav(
        skeKravidentifikator: String,
        corrID: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSentKrav(corrID, skeKravidentifikator, responseStatus)
        }
    }

    private fun updateSentKrav(
        corrID: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSentKrav(corrID, responseStatus)
        }
    }

    private fun updateSentKrav(
        saveCorrID: String,
        searchCorrID: String,
        type: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSentKrav(saveCorrID, searchCorrID, type, responseStatus)
        }
    }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertAllNewKrav(kravLinjer)
        }
    }

    fun getAllErrorMessages(): List<FeilmeldingTable> {
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.getAllErrorMessages()
        }
    }



    fun saveErrorMessage(feilMelding: FeilmeldingTable, corrID: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertErrorMessage(feilMelding, corrID)
        }
    }

    fun updateSentKrav(
        responses: List<Map<String, RequestResult>>,
    ) {
        responses.forEach {
            it.entries.forEach { entry ->

                Metrics.numberOfKravSent.inc()
                Metrics.typeKravSent.labels(entry.value.krav.kravkode).inc()

                if  (entry.value.krav.kravtype == NYTT_KRAV) updateSentKrav(
                            entry.value.kravIdentifikator,
                            entry.value.corrId,
                            entry.value.status.value
                        )

                    else updateSentKrav(
                            entry.value.corrId,
                            entry.value.status.value
                        )

            }

        }
    }

    suspend fun saveErrorMessage(
        request: String,
        response: HttpResponse,
        krav: KravTable,
        kravIdentifikator: String,
        corrID: String,
    ) {
        val skeKravidentifikator =
            if (kravIdentifikator == krav.saksnummerNAV || kravIdentifikator == krav.referanseNummerGammelSak) "" else kravIdentifikator

        val feilResponse = response.body<FeilResponse>()

        if (feilResponse.status == 404) {
            //handleHvaFGjorViNaa(krav)
        }
        val feilmelding = FeilmeldingTable(
            0L,
            getKravTableIdFromCorrelationId(corrID),
            corrID,
            krav.saksnummerSKE,
            skeKravidentifikator,
            feilResponse.status.toString(),
            feilResponse.detail,
            request,
            response.bodyAsText(),
            LocalDateTime.now(),
        )

        saveErrorMessage(feilmelding, corrID)
    }

    fun getAllKravForStatusCheck(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForStatusCheck()
        }
    }

    fun getAllKravForAvstemming(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForAvstemming()
        }
    }

    fun getErrorMessageForKravId(kravId: Int): List<FeilmeldingTable>{
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.getErrorMessageForKravId(kravId)
        }
    }

    fun updateStatus(mottakStatus: String, corrId: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateStatus(mottakStatus, corrId)
        }
    }

    fun updateStatusForAvstemtKravToReported(kravId: Int) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateStatusForAvstemtKravToReported(kravId)
        }
    }

    fun getAllKravForResending(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForResending()
        }
    }

    fun getAllUnsentKrav(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllUnsentKrav()
        }
    }

    fun updateEndringWithSkeKravIdentifikator(navsaksnummer: String, skeKravidentifikator: String) {
        postgresDataSource.connection.useAndHandleErrors {
            con -> con.updateEndringWithSkeKravIdentifikator(navsaksnummer, skeKravidentifikator)
        }
    }
}