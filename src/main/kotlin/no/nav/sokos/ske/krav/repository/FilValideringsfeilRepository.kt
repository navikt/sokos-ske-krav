package no.nav.sokos.ske.krav.repository

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil

object FilValideringsfeilRepository {
    fun getAllFilValideringsFeil(tx: TransactionalSession): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                """
                select * from filvalideringsfeil
                """.trimIndent(),
            ),
            extractor = mapToFilValideringsfeil,
        )

    fun getFilValideringsFeilForLinje(
        tx: TransactionalSession,
        filNavn: String,
        linjeNummer: Int,
    ): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                """
                select * from filvalideringsfeil
                where filnavn = ? and linjenummer = ?
                """.trimIndent(),
                filNavn,
                linjeNummer,
            ),
            extractor = mapToFilValideringsfeil,
        )

    fun getFilValideringsFeilForFil(
        tx: TransactionalSession,
        filNavn: String,
    ): List<FilValideringsfeil> =
        tx.list(
            queryOf(
                """
                select * from filvalideringsfeil
                where filnavn = ?
                """.trimIndent(),
                filNavn,
            ),
            extractor = mapToFilValideringsfeil,
        )

    fun insertFileValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        feilmelding: String,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                insert into filvalideringsfeil (filnavn, feilmelding)
                values (?, ?)
                """.trimIndent(),
                filnavn,
                feilmelding,
            ),
        )

    fun insertLineFilValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ): Int =
        tx.update(
            queryOf(
                """
                insert into filvalideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
                values (?, ?, ?, ?, ? )
                """.trimIndent(),
                filnavn,
                kravlinje.linjenummer,
                kravlinje.saksnummerNav,
                kravlinje.toString(),
                feilmelding,
            ),
        )

    private val mapToFilValideringsfeil: (Row) -> FilValideringsfeil = { row ->
        FilValideringsfeil(
            valideringsfeilId = row.long("id"),
            filnavn = row.string("filnavn"),
            linjenummer = row.intOrNull("linjenummer") ?: 0,
            saksnummerNav = row.stringOrNull("saksnummer_nav") ?: "",
            kravLinje = row.stringOrNull("kravlinje") ?: "",
            feilmelding = row.string("feilmelding"),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
            rapporter = row.boolean("rapporter"),
        )
    }
}
