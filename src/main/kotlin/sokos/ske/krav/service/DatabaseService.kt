package sokos.ske.krav.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.getAllKravForReconciliation
import sokos.ske.krav.database.Repository.insertNewKobling
import sokos.ske.krav.database.Repository.insertNewKrav
import sokos.ske.krav.database.Repository.saveErrorMessage
import sokos.ske.krav.database.Repository.saveValidationError
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

    fun insertNewKobling(saksnummerNav: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.insertNewKobling(saksnummerNav)
        }
    }

    fun insertNewKrav(
        skeKravident: String,
        kravLinje: KravLinje,
        kravtype: String,
        responseStatus: String
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.insertNewKrav(skeKravident, kravLinje, kravtype, responseStatus)
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

    private suspend fun determineStatus(responses: Map<String, HttpResponse>, response: HttpResponse): String {
        return if (responses.filter { resp -> resp.value.status.isSuccess() }.size == responses.size) Status.KRAV_SENDT.value
        else if (response.status.isSuccess()) Status.FEIL_MED_ENDRING.value
        else {
            val feilResponse = response.body<FeilResponse>()
            if (feilResponse.status == 404 && feilResponse.type.contains("innkrevingsoppdrag-eksisterer-ikke")) Status.FANT_IKKE_SAKSREF.value
            else if (feilResponse.status == 409 && feilResponse.detail.contains("reskontroført")) Status.IKKE_RESKONTROFORT.value
            else "UKJENT STATUS: ${responses.map { resp -> "${resp.value.status.value}: ${ resp.value.body<FeilResponse>().type}" }}"
        }

    }

    suspend fun saveSentKravToDatabase(responses: Map<String, HttpResponse>, krav: KravLinje, kravident: String) {
        var kravidentToBeSaved = kravident
        responses.forEach { entry ->

            Metrics.numberOfKravSent.inc()
            Metrics.typeKravSent.labels(krav.stonadsKode).inc()

            val statusString = determineStatus(responses, entry.value)

            if (!krav.isNyttKrav() && kravidentToBeSaved == krav.referanseNummerGammelSak) kravidentToBeSaved = ""

            insertNewKrav(
                kravidentToBeSaved,
                krav,
                entry.key,
                statusString
            )
        }

    }

    suspend fun saveErrorMessageToDatabase(request: String, response: HttpResponse, krav: KravLinje, kravident: String) {
        if (response.status.isSuccess()) return
        val kravidentSke = if (kravident == krav.saksNummer || kravident == krav.referanseNummerGammelSak) "" else kravident

        val feilResponse = response.body<FeilResponse>()

        if (feilResponse.status == 404) {
            //handleHvaFGjorViNaa(krav)
        }
        val feilmelding = FeilmeldingTable(
            0L,
            0L,
            krav.saksNummer,
            kravidentSke,
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