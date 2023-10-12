package sokos.ske.krav.service

import io.ktor.client.statement.*
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
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse
import sokos.ske.krav.skemodels.responses.SokosValideringsfeil

class KravService(
    private val postgresDataSource: PostgresDataSource
) {

    fun hentSkeKravident(navref: String): String{
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.hentSkeKravIdent(navref)
        }
    }


    fun lagreNyKobling(saksnummerNav: String): String {
        postgresDataSource.connection.useAndHandleErrors {con ->
            return con.lagreNyKobling(saksnummerNav)
        }
    }

    fun lagreNyttKrav(skeKravident: String, request: String, filLinje: String, detailLine: DetailLine, kravtype: String, response: HttpResponse){
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.lagreNyttKrav(skeKravident, request, filLinje, detailLine, kravtype, response)
        }
    }

    fun hentAlleKravMedValideringsfeil(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentAlleKravMedValideringsfeil()
        }
    }

    fun lagreValideringsfeil(sokosValideringsfeil: SokosValideringsfeil){
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.lagreValideringsfeil(sokosValideringsfeil)
        }
    }

    fun hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        postgresDataSource.connection.useAndHandleErrors { con ->
            return con.hentAlleKravSomIkkeErReskotrofort()
        }
    }

    fun oppdaterStatus(mottakStatus: MottaksstatusResponse){
        postgresDataSource.connection.useAndHandleErrors { con ->
            con.oppdaterStatus(mottakStatus)
        }
    }
}