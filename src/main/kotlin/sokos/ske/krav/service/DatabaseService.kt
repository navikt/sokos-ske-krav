package sokos.ske.krav.service

import io.ktor.http.HttpStatusCode
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.hentAlleKravMedValideringsfeil
import sokos.ske.krav.database.Repository.hentAlleKravSomIkkeErReskotrofort
import sokos.ske.krav.database.Repository.hentSkeKravIdent
import sokos.ske.krav.database.Repository.lagreNyKobling
import sokos.ske.krav.database.Repository.lagreNyttKrav
import sokos.ske.krav.database.Repository.lagreValideringsfeil
import sokos.ske.krav.database.Repository.oppdaterStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse

class DatabaseService(
    private val postgresDataSource: PostgresDataSource
) {

    fun hentSkeKravident(navref: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentSkeKravIdent(navref)
        }
    }


    fun lagreNyKobling(saksnummerNav: String): String {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.lagreNyKobling(saksnummerNav)
        }
    }

    fun lagreNyttKrav(
        skeKravident: String,
        request: String,
        kravLinje: KravLinje,
        kravtype: String,
        responseStatus: HttpStatusCode
    ) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.lagreNyttKrav(skeKravident, request, kravLinje, kravtype, responseStatus)
        }
    }

    fun hentAlleKravMedValideringsfeil(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentAlleKravMedValideringsfeil()
        }
    }

    fun lagreValideringsfeil(valideringsFeilResponse: ValideringsFeilResponse, saksnummerSKE: String) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.lagreValideringsfeil(valideringsFeilResponse, saksnummerSKE)
        }
    }

    fun hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentAlleKravSomIkkeErReskotrofort()
        }
    }

    fun oppdaterStatus(mottakStatus: MottaksStatusResponse) {
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.oppdaterStatus(mottakStatus)
        }
    }
}