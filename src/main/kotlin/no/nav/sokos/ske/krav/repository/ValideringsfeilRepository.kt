package no.nav.sokos.ske.krav.repository

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.Valideringsfeil
import no.nav.sokos.ske.krav.dto.nav.KravLinje

class ValideringsfeilRepository(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun getValideringsFeilForLinje(
        filNavn: String,
        linjeNummer: Int,
    ): List<Valideringsfeil> =
        using(sessionOf(dataSource)) { session ->
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
        }

    fun getValideringsFeilForFil(filNavn: String): List<Valideringsfeil> =
        using(sessionOf(dataSource)) { session ->
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
        }

    fun insertFileValideringsfeil(
        filnavn: String,
        feilmelding: String,
        session: Session,
    ) {
        session.run(
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
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
        session: Session,
    ) {
        session.update(
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
