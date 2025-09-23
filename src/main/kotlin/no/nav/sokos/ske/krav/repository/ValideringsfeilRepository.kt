package no.nav.sokos.ske.krav.repository

import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.Valideringsfeil
import no.nav.sokos.ske.krav.dto.nav.KravLinje

object ValideringsfeilRepository {
    fun getValideringsFeilForLinje(
        session: Session,
        filNavn: String,
        linjeNummer: Int,
    ): List<Valideringsfeil> =
        session.list(
            queryOf(
                """
                select * from valideringsfeil
                where filnavn = ? and linjenummer = ?
                """.trimIndent(),
                filNavn,
                linjeNummer,
            ),
            mapToValideringsfeil,
        )

    fun getValideringsFeilForFil(
        session: Session,
        filNavn: String,
    ): List<Valideringsfeil> =
        session.list(
            queryOf(
                """
                select * from valideringsfeil
                where filnavn = ?
                """.trimIndent(),
                filNavn,
            ),
            mapToValideringsfeil,
        )

    fun insertFileValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        feilmelding: String,
    ) {
        tx.run(
            queryOf(
                """
                insert into valideringsfeil (filnavn, feilmelding)
                values (?, ?)
                """.trimIndent(),
                filnavn,
                feilmelding,
            ).asUpdateAndReturnGeneratedKey,
        )
    }

    fun insertLineValideringsfeil(
        tx: TransactionalSession,
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        tx.update(
            queryOf(
                """
                insert into valideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
                values (?, ?, ?, ?, ? )
                """.trimIndent(),
                filnavn,
                kravlinje.linjenummer,
                kravlinje.saksnummerNav,
                kravlinje.toString(),
                feilmelding,
            ),
        )
    }

    private val mapToValideringsfeil: (Row) -> Valideringsfeil = { row ->
        Valideringsfeil(
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
