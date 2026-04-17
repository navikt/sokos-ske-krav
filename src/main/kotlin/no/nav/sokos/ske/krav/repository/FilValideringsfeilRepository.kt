package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil

object FilValideringsfeilRepository {
    internal val mapToFilValideringsfeil: (Row) -> FilValideringsfeil = { row ->
        FilValideringsfeil(
            valideringsfeilId = row.long("id"),
            filnavn = row.string("filnavn"),
            linjenummer = row.int("linjenummer"),
            saksnummerNav = row.string("saksnummer_nav"),
            kravLinje = row.string("kravlinje"),
            feilmelding = row.string("feilmelding"),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
            rapporter = row.boolean("rapporter"),
        )
    }

    fun getFilValideringsFeilForLinje(
        tx: TransactionalSession,
        filNavn: String,
        linjeNummer: Int,
    ): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                "select * from filvalideringsfeil where filnavn = ? and linjenummer = ?",
                filNavn,
                linjeNummer,
            ),
            mapToFilValideringsfeil,
        )

    fun getFilValideringsFeilForFil(
        tx: TransactionalSession,
        filNavn: String,
    ): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                "select * from filvalideringsfeil where filnavn = ?",
                filNavn,
            ),
            mapToFilValideringsfeil,
        )

    fun insertFileValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        feilmelding: String,
    ) = tx.update(
        queryOf(
            "insert into filvalideringsfeil (filnavn, feilmelding) values (?, ?)",
            filnavn,
            feilmelding,
        ),
    )

    fun insertLineFilValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = tx.update(
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

    fun deleteOldFilValideringsfeil(
        tx: TransactionalSession,
        threshold: LocalDate,
    ): Int =
        tx.update(
            queryOf(
                "delete from filvalideringsfeil where tidspunkt_opprettet < ?",
                threshold,
            ),
        )
}
