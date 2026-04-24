package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.util.DBUtils.transaction

class FilValideringsfeilRepository(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    val mapToFilValideringsfeil: (Row) -> FilValideringsfeil = { row ->
        FilValideringsfeil(
            valideringsfeilId = row.long("id"),
            filnavn = row.string("filnavn"),
            linjenummer = row.intOrNull("linjenummer") ?: 0,
            saksnummerNav = row.stringOrNull("saksnummer_nav") ?: "",
            kravLinje = row.stringOrNull("kravlinje") ?: "",
            feilmelding = row.string("feilmelding").trim(),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
            rapporter = row.boolean("rapporter"),
        )
    }

    fun <A> transaction(operation: (TransactionalSession) -> A): A = dataSource.transaction(operation)

    fun getFilValideringsFeilForFil(filnavn: String): List<FilValideringsfeil> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    "select * from filvalideringsfeil where filnavn = ?",
                    filnavn,
                ),
                extractor = mapToFilValideringsfeil,
            )
        }

    fun insertFilValideringsfeil(
        filnavn: String,
        feilmelding: String,
    ) {
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    insert into filvalideringsfeil (filnavn, feilmelding)
                    values (?, ?)
                    """.trimIndent(),
                    filnavn,
                    feilmelding,
                ),
            )
        }
    }

    fun insertLineFilValideringsfeil(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    insert into filvalideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
                    values (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    filnavn,
                    kravlinje.linjenummer,
                    kravlinje.saksnummerNav,
                    kravlinje.toString(),
                    feilmelding,
                ),
            )
        }
    }

    fun deleteOldFilValideringsfeil(threshold: LocalDate): Int =
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    delete from filvalideringsfeil where tidspunkt_opprettet < ?
                    """.trimIndent(),
                    threshold,
                ),
            )
        }

    companion object {
        val instance: FilValideringsfeilRepository by lazy { FilValideringsfeilRepository() }
    }
}
