package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllKravForReconciliation
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getKravIdfromCorrId
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertNewKobling
import sokos.ske.krav.database.Repository.insertNewKrav
import sokos.ske.krav.database.Repository.saveErrorMessage
import sokos.ske.krav.database.Repository.saveValidationError
import sokos.ske.krav.database.Repository.updateSendtKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.util.isNyttKrav
import java.time.LocalDateTime

class DatabaseService(
    private val postgresDataSource: PostgresDataSource = PostgresDataSource()
) {

    fun getSkeKravident(navref: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getSkeKravIdent(navref)
        }
    }

    fun getKravIdFromCorrId(corrID: String): Long {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getKravIdfromCorrId(corrID)
        }
    }

    fun insertNewKobling(saksnummerNav: String, corrID: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.insertNewKobling(saksnummerNav, corrID)
        }
    }

    fun insertNewKrav(
        skeKravident: String,
        corrID: String,
        kravLinje: KravLinje,
        kravtype: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertNewKrav(skeKravident, corrID,  kravLinje, kravtype, responseStatus)
        }
    }
    private fun updateSendtKrav(
        kravIdentifikatorSke: String,
        corrID: String,
        kravtype: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            println("Updating krav med corrID $corrID, kravidentSKE $kravIdentifikatorSke, kravtype $kravtype, responsestatus $responseStatus")
            if(kravtype == NYTT_KRAV)   con.updateSendtKrav(corrID, kravIdentifikatorSke, responseStatus)
            else con.updateSendtKrav(corrID, responseStatus)
        }
    }
    fun saveAllNewKrav(
        kravLinjer: List<KravLinje>,
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertAllNewKrav(kravLinjer)
        }
    }

    fun getAlleKravMedValideringsfeil(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllValidationErrors()
        }
    }

    fun saveValideringsfeil(valideringsFeilResponse: ValideringsFeilResponse, saksnummerSKE: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.saveValidationError(valideringsFeilResponse, saksnummerSKE)
        }
    }

    fun saveFeilmelding(feilMelding: FeilmeldingTable){
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.saveErrorMessage(feilMelding)
        }
    }

    private suspend fun determineStatus(responses: Map<String, SkeService.RequestResult>, response: HttpResponse): String {
        return if (responses.filter { resp -> resp.value.response.status.isSuccess() }.size == responses.size) Status.KRAV_SENDT.value
        else if (response.status.isSuccess()) Status.FEIL_MED_ENDRING.value
        else {
            val feilResponse = response.body<FeilResponse>()
            if (feilResponse.status == 404 && feilResponse.type.contains("innkrevingsoppdrag-eksisterer-ikke")) Status.FANT_IKKE_SAKSREF.value
            else if (feilResponse.status == 409 && feilResponse.detail.contains("reskontroført")) Status.IKKE_RESKONTROFORT.value
            else "UKJENT STATUS: ${responses.map { resp -> "${resp.value.response.status.value}: ${ resp.value.response.body<FeilResponse>().type}" }}"
        }

    }

    suspend fun updateSentKravToDatabase(responses: Map<String, SkeService.RequestResult>, krav: KravLinje, kravident: String) {
        var kravidentToBeSaved = kravident
        responses.forEach { entry ->

            Metrics.numberOfKravSent.inc()
            Metrics.typeKravSent.labels(krav.stonadsKode).inc()

            val statusString = determineStatus(responses, entry.value.response)

            if (!krav.isNyttKrav() && kravidentToBeSaved == krav.referanseNummerGammelSak) kravidentToBeSaved = ""

            updateSendtKrav(
                kravidentToBeSaved,
                entry.value.corrId,
                entry.key,
                statusString
            )
        }

    }
    suspend fun saveErrorMessageToDatabase(request: String, response: HttpResponse, krav: KravLinje, kravIdentifikator: String, corrID: String) {
        val kravIdentifikatorSke = if (kravIdentifikator == krav.saksNummer || kravIdentifikator == krav.referanseNummerGammelSak) "" else kravIdentifikator

        val feilResponse = response.body<FeilResponse>()

        if (feilResponse.status == 404) {
            //handleHvaFGjorViNaa(krav)
        }
        val feilmelding = FeilmeldingTable(
            0L,
            getKravIdFromCorrId(corrID),
            krav.saksNummer,
            kravIdentifikatorSke,
            feilResponse.status.toString(),
            feilResponse.detail,
            request,
            response.bodyAsText(),
            LocalDateTime.now()
        )

        saveFeilmelding(feilmelding)
    }

    fun hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForStatusCheck()
        }
    }

    fun hentAlleKravSomSkalAvstemmes(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAllKravForReconciliation()
        }
    }

    fun updateStatus(mottakStatus: MottaksStatusResponse) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateStatus(mottakStatus)
        }
    }
}