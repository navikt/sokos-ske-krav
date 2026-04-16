package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil

object FilValideringsfeilRepository {
    fun getFilValideringsFeilForFil(
        tx: TransactionalSession,
        filnavn: String,
    ): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                "select * from filvalideringsfeil where filnavn = ?",
                filnavn,
            ),
            extractor = mapToFilValideringsfeil,
        )

    fun insertFilValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        feilmelding: String,
    ) {
        tx.update(
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

    fun insertLineFilValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        tx.update(
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

    fun deleteOldFilValideringsfeil(
        tx: TransactionalSession,
        threshold: LocalDate,
    ): Int =
        tx.update(
            queryOf(
                """
                delete from filvalideringsfeil where tidspunkt_opprettet < ?
                """.trimIndent(),
                threshold,
            ),
        )

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
}
