package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllFeilmeldinger
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllKravForValidering
import sokos.ske.krav.database.Repository.getAllKravNotSent
import sokos.ske.krav.database.Repository.getAlleKravForAvstemming
import sokos.ske.krav.database.Repository.getFeillinjeForKravId
import sokos.ske.krav.database.Repository.getKravIdfromCorrId
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.saveErrorMessage
import sokos.ske.krav.database.Repository.setSkeKravIdentPaEndring
import sokos.ske.krav.database.Repository.updateAvstemtKrav
import sokos.ske.krav.database.Repository.updateSendtKrav
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

    fun getSkeKravident(navref: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getSkeKravIdent(navref)
        }
    }

    private fun getKravIdFromCorrId(corrID: String): Long {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getKravIdfromCorrId(corrID)
        }
    }

    private fun updateSendtKrav(
        kravIdentifikatorSke: String,
        corrID: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSendtKrav(corrID, kravIdentifikatorSke, responseStatus)
        }
    }

    private fun updateSendtKrav(
        corrID: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSendtKrav(corrID, responseStatus)
        }
    }

    private fun updateSendtKrav(
        saveCorrID: String,
        searchCorrID: String,
        type: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateSendtKrav(saveCorrID, searchCorrID, type, responseStatus)
        }
    }

    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertAllNewKrav(kravLinjer)
        }
    }

    fun getAllFeilmeldinger(): List<FeilmeldingTable> {
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.getAllFeilmeldinger()
        }
    }



    fun saveFeilmelding(feilMelding: FeilmeldingTable, corrID: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.saveErrorMessage(feilMelding, corrID)
        }
    }

    fun updateSentKravToDatabase(
        responses: List<Map<String, RequestResult>>,
    ) {
        responses.forEach {
            it.entries.forEach { entry ->

                Metrics.numberOfKravSent.inc()
                Metrics.typeKravSent.labels(entry.value.krav.kravkode).inc()

                when {
                    entry.value.krav.kravtype == NYTT_KRAV ->
                        updateSendtKrav(
                            entry.value.kravIdentifikator,
                            entry.value.corrId,
                            entry.value.status.value
                        )

                    (entry.value.corrId == entry.value.krav.corr_id) ->
                        updateSendtKrav(
                            entry.value.corrId,
                            entry.value.status.value
                        )

                    else -> {
                        updateSendtKrav(
                            entry.value.corrId,
                            entry.value.krav.corr_id,
                            entry.key,
                            entry.value.status.value
                        )
                    }
                }

            }

        }
    }

    suspend fun saveErrorMessageToDatabase(
        request: String,
        response: HttpResponse,
        krav: KravTable,
        kravIdentifikator: String,
        corrID: String,
    ) {
        val kravIdentifikatorSke =
            if (kravIdentifikator == krav.saksnummerNAV || kravIdentifikator == krav.referanseNummerGammelSak) "" else kravIdentifikator

        val feilResponse = response.body<FeilResponse>()

        if (feilResponse.status == 404) {
            //handleHvaFGjorViNaa(krav)
        }
        val feilmelding = FeilmeldingTable(
            0L,
            getKravIdFromCorrId(corrID),
            corrID,
            krav.saksnummerSKE,
            kravIdentifikatorSke,
            feilResponse.status.toString(),
            feilResponse.detail,
            request,
            response.bodyAsText(),
            LocalDateTime.now(),
        )

        saveFeilmelding(feilmelding, corrID)
    }

    fun hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForStatusCheck()
        }
    }

    fun hentKravSomSkalAvstemmes(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAlleKravForAvstemming()
        }
    }

    fun hentFeillinjeForKravid(kravId: Int): List<FeilmeldingTable>{
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.getFeillinjeForKravId(kravId)
        }
    }

    fun updateStatus(mottakStatus: String, corrId: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateStatus(mottakStatus, corrId)
        }
    }

    fun updateAvstemtKrav(kravId: Int) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateAvstemtKrav(kravId)
        }
    }

    fun hentKravSomSkalResendes(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForResending()
        }
    }

    fun hentAlleKravSomSkalValideres(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForValidering()
        }
    }

    fun hentAlleKravSomIkkeErSendt(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravNotSent()
        }
    }

    fun updateSkeKravidentifikator(navsaksnummer: String, skeKravidentifikator: String) {
        postgresDataSource.connection.useAndHandleErrors {
            con -> con.setSkeKravIdentPaEndring(navsaksnummer, skeKravidentifikator)
        }
    }
}