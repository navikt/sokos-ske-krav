package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toKobling
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse
import sokos.ske.krav.skemodels.responses.SokosValideringsfeil
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

const val STATUS_RESKONTROFORT = "RESKONTROFOERT"
const val STATUS_VALIDERINGSFEIL = "VALIDERINGSFEIL"
const val STATUS_UNDER_BEHANDLING = "MOTTATT_UNDER_BEHANDLING"

object Repository {
    private val logger = KotlinLogging.logger {}


    fun Connection.hentAlleKravData(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav""").executeQuery().toKrav()
        } catch (e: Exception) {
            logger.error("exception i henting av data: ${e.message}")
            listOf()
        }
    }

    fun Connection.hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav where status <> ? and status <> ?""")
                .withParameters(
                    param(STATUS_RESKONTROFORT),
                    param(STATUS_VALIDERINGSFEIL)
                ).executeQuery().toKrav()
        } catch (e: Exception) {
            logger.error { "exception i henting (status) av data: ${e.message}" }
            listOf()
        }
    }


    fun Connection.hentAlleKravMedValideringsfeil(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav where status = ?""")
                .withParameters(
                    param(STATUS_VALIDERINGSFEIL)
                ).executeQuery().toKrav()
        } catch (e: Exception) {
            logger.error { "exception i henting (validering) av data: ${e.message}" }
            listOf()
        }
    }

    fun Connection.lagreNyttKrav(
        skeid: String,
        request: String,
        filLinje: String,
        detailLinje: DetailLine,
        kravtype: String
    ) {
        try {
            val now = LocalDateTime.now()
            println("Lagrer ny tildb: $skeid $now, $filLinje $request")
            prepareStatement(
                """
                insert into krav (
                saksnummer_nav, 
                saksnummer_ske, 
                fildata_nav, 
                jsondata_ske, 
                status, 
                dato_sendt, 
                dato_siste_status,
                kravtype
                ) values (?,?,?,?,?,?,?,?)
            """.trimIndent()
            ).withParameters(
                param(detailLinje.saksNummer),
                param(skeid),
                param(filLinje),
                param(request),
                param("SENDT"),
                param(now),
                param(now),
                param(kravtype)
            ).execute()
            commit()
            println("lagring av $skeid OK")
        } catch (e: Exception) {
            println("lagring av $skeid feilet")
            println("exception lagring av nytt krav: ${e.message}")
        }
    }


    fun Connection.hentSkeKravIdent(navref: String): String {
        val rs = prepareStatement("""
            select distinct(saknummer_ske) from krav
            where saknummer_nav = ?
        """.trimIndent()
        ).withParameters(
            param(navref)
        ).executeQuery()
        if (rs.next())
            return rs.getColumn("saksnummer_ske")
        else return ""

    }
    fun Connection.lagreNyKobling(ref: String): String {
        val nyref = UUID.randomUUID().toString()
        prepareStatement(
            """
            insert into kobling (
            saksref_fil,
            saksref_uuid,
            dato
            ) values (?, ?, ?)
        """.trimIndent()
        ).withParameters(
            param(ref),
            param(nyref),
            param(LocalDateTime.now())
        ).execute()
        commit()

        return nyref
    }

    fun Connection.koblesakRef(filref: String): String {
        val rs = prepareStatement(
            """
            select distinct(saksref_uuid) from kobling
            where saksref_fil = ?
        """.trimIndent()
        ).withParameters(
            param(filref)
        ).executeQuery()
        if (rs.next())
            return rs.getColumn("saksref_uuid")
        else return ""
    }

    fun Connection.hentAlleKoblinger(): List<KoblingTable> {
        return prepareStatement(
            """
            select * from kobling
        """.trimIndent()
        ).executeQuery().toKobling()
    }

    fun Connection.oppdaterStatus(mottakStatus: MottaksstatusResponse) {
        logger.info { "Logger repos: Lagrer mottaksstatus: ${mottakStatus}" }
        prepareStatement(
            """
            update krav 
            set status = ?, dato_siste_status = ?
            where saksnummer_ske = ?
        """.trimIndent().also { logger.info { it } }
        )
            .withParameters(
                param(mottakStatus.mottaksstatus),
                param(LocalDateTime.now()),
                param(mottakStatus.kravidentifikator)
            ).execute()
        commit()
    }

    fun Connection.lagreValideringsfeil(sokosValideringsfeil: SokosValideringsfeil) {
        logger.info { "logger repos: Lagrer valideringsfeil: ${sokosValideringsfeil.kravidSke}" }
        sokosValideringsfeil.valideringsfeilResponse.valideringsfeil.forEach {
            prepareStatement(
                """
                insert into validering (
                    saksnummer_ske,
                    error,
                    melding,
                    dato
                ) 
                values (?, ?, ?, ?)
            """.trimIndent().also { logger.info { it } }
            ).withParameters(
                param(sokosValideringsfeil.kravidSke),
                param(it.error),
                param(it.message),
                param(LocalDate.now())
            ).execute()
        }
        commit()
    }
}
