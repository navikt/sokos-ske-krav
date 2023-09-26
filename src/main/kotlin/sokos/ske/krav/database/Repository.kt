package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toList
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.navmodels.DetailLine
import java.sql.Connection
import java.time.LocalDateTime

const val STATUS_RESKONTROFORT = "RESKONTROFOERT"
const val STATUS_VALIDERINGSFEIL = "VALIDERINGSFEIL"
const val STATUS_UNDER_BEHANDLING = "MOTTATT_UNDER_BEHANDLING"
object Repository {
    private val log = KotlinLogging.logger {}


    fun Connection.hentAlleKravData(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav""").executeQuery().toList {
                KravTable(
                    krav_id = getColumn("krav_id"),
                    saksnummer_nav = getColumn("saksnummer_nav"),
                    saksnummer_ske = getColumn("saksnummer_ske"),
                    fildata_nav = getColumn("fildata_nav"),
                    jsondata_ske = getColumn("jsondata_ske"),
                    status = getColumn("status"),
                    dato_sendt = getColumn("dato_sendt"),
                    dato_siste_status = getColumn("dato_siste_status")
                )
            }
        } catch (e: Exception) {
            log.error("exception i henting av data: ${e.message}")
            listOf()
        }
    }

    fun Connection.hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav where status <> ?""")
                .withParameters(
                    param(STATUS_RESKONTROFORT)
                ).executeQuery().toList {
                    KravTable(
                        krav_id = getColumn("krav_id"),
                        saksnummer_nav = getColumn("saksnummer_nav"),
                        saksnummer_ske = getColumn("saksnummer_ske"),
                        fildata_nav = getColumn("fildata_nav"),
                        jsondata_ske = getColumn("jsondata_ske"),
                        status = getColumn("status"),
                        dato_sendt = getColumn("dato_sendt"),
                        dato_siste_status = getColumn("dato_siste_status")
                    )
                }
        } catch (e: Exception) {
            log.error("exception i henting av data: ${e.message}")
            listOf()
        }
    }

    fun Connection.hentAlleKravMedValideringsfeil(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav where status = ?""")
                .withParameters(
                    param(STATUS_VALIDERINGSFEIL)
                ).executeQuery().toList {
                    KravTable(
                        krav_id = getColumn("krav_id"),
                        saksnummer_nav = getColumn("saksnummer_nav"),
                        saksnummer_ske = getColumn("saksnummer_ske"),
                        fildata_nav = getColumn("fildata_nav"),
                        jsondata_ske = getColumn("jsondata_ske"),
                        status = getColumn("status"),
                        dato_sendt = getColumn("dato_sendt"),
                        dato_siste_status = getColumn("dato_siste_status")
                    )
                }
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
                fildata_nav, 
                jsondata_ske, 
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
                select * from pg_catalog.pg_namespace
            """.trimIndent()
    ).executeQuery().toList {
        getColumn<String>("nspname").toString()
    }
}
