package sokos.ske.krav.service

import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getAlleKravSomIkkeErReskotrofort
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.hentAlleKravSomSkalAvstemmes
import sokos.ske.krav.database.Repository.insertNewKobling
import sokos.ske.krav.database.Repository.insertNewKrav
import sokos.ske.krav.database.Repository.saveFeilmelding
import sokos.ske.krav.database.Repository.saveValideringsfeil
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse

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
            con.saveValideringsfeil(valideringsFeilResponse, saksnummerSKE)
        }
    }

    fun saveFeilmelding(feilMelding: FeilmeldingTable){
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.saveFeilmelding(feilMelding)
        }
    }

    fun hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.getAlleKravSomIkkeErReskotrofort()
        }
    }

    fun hentAlleKravSomSkalAvstemmes(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentAlleKravSomSkalAvstemmes()
        }
    }

    fun updateStatus(mottakStatus: MottaksStatusResponse) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.updateStatus(mottakStatus)
        }
    }
}