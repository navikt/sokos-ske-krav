package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toList
import sokos.ske.krav.database.RepositoryExtensions.toOpprettInnkrevingsOppdragResponse
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import java.sql.Connection
import java.time.LocalDateTime

object Repository {
    private val log = KotlinLogging.logger {}
    fun Connection.hentKravData(): List<OpprettInnkrevingsOppdragResponse> {
        return try {
            prepareStatement("""select * from krav""").executeQuery().toOpprettInnkrevingsOppdragResponse()
        } catch (e: Exception) {
            log.error("exception i henting av data: ${e.message}")
            listOf()
        }
    }

    fun Connection.lagreNyttKrav(skeid: String, request: String, filLinje: String, detailLinje: DetailLine) {
        try {
            val now = LocalDateTime.now()
            println("Lagrer tildb: $skeid $now, $filLinje $request")
            prepareStatement(
                """
                insert into krav (
                saksnummer_nav, 
                saksnummer_ske, 
                fildatanav, 
                jsondataske, 
                status, 
                dato_sendt, 
                dato_siste_status
                ) values (?,?,?,?,?,?,?)
            """.trimIndent()
            ).withParameters(
                param(detailLinje.saksNummer),
                param(skeid),
                param(filLinje),
                param(request),
                param("SENDT"),
                param(now),
                param(now)
            ).execute()
            println("Committer $skeid")
            commit()
            println("lagring av $skeid OK")
        } catch (e: Exception) {
            println("lagring av $skeid feilet")
            println("exception lagring av nytt krav: ${e.message}")
        }
    }

    fun Connection.hentTabeller(): List<String> = prepareStatement(
            """
                select nspname, nspowner from pg_catalog.pg_namespace
            """.trimIndent()
        ).executeQuery().toList {
            "Schema: " + getColumn("nspname") + " Eier: " + getColumn("nspowner")
        }
}
