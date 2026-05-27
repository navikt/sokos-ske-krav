package no.nav.sokos.ske.krav.repository

import java.time.LocalDate
import javax.sql.DataSource

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil

class FilValideringsfeilRepository(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
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

    fun insertFilValideringsfeil(
        session: TransactionalSession,
        filnavn: String,
        feilmelding: String,
    ) {
        session.update(
            queryOf(
                // language=SQL
                """
                insert into filvalideringsfeil (filnavn, feilmelding)
                values (:filnavn, :feilmelding)
                """.trimIndent(),
                mapOf(
                    "filnavn" to filnavn,
                    "feilmelding" to feilmelding,
                ),
            ),
        )
    }

    fun insertLineFilValideringsfeil(
        session: TransactionalSession,
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        session.update(
            queryOf(
                // language=SQL
                """
                insert into filvalideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
                values (:filnavn, :linjenummer, :saksnummerNav, :kravlinje, :feilmelding)
                """.trimIndent(),
                mapOf(
                    "filnavn" to filnavn,
                    "linjenummer" to kravlinje.linjenummer,
                    "saksnummerNav" to kravlinje.saksnummerNav,
                    "kravlinje" to kravlinje.toString(),
                    "feilmelding" to feilmelding,
                ),
            ),
        )
    }

    fun deleteOldFilValideringsfeil(
        session: TransactionalSession,
        threshold: LocalDate,
    ): Int =
        session.update(
            queryOf(
                // language=SQL
                """
                delete from filvalideringsfeil where tidspunkt_opprettet < ?
                """.trimIndent(),
                threshold,
            ),
        )

    companion object {
        val instance: FilValideringsfeilRepository by lazy { FilValideringsfeilRepository() }
    }
}
