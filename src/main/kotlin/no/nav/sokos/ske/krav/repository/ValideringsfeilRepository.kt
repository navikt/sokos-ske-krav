package no.nav.sokos.ske.krav.repository

import java.sql.Connection

import no.nav.sokos.ske.krav.domain.Valideringsfeil
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeUpdate

object ValideringsfeilRepository {
    fun Connection.getValideringsFeilForLinje(
        filNavn: String,
        linjeNummer: Int,
    ): List<Valideringsfeil> =
        executeSelect(
            """
            select * from valideringsfeil
            where filnavn = ? and linjenummer = ?
            """,
            filNavn,
            linjeNummer,
        ).toValideringsfeil()

    fun Connection.getValideringsFeilForFil(filNavn: String): List<Valideringsfeil> =
        executeSelect(
            """
            select * from valideringsfeil
            where filnavn = ?
            """,
            filNavn,
        ).toValideringsfeil()

    fun Connection.insertFileValideringsfeil(
        filnavn: String,
        feilmelding: String,
    ) = executeUpdate(
        """
            insert into valideringsfeil (filnavn, feilmelding)
            values (?, ?)
            """,
        filnavn,
        feilmelding,
    )

    fun Connection.insertLineValideringsfeil(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = executeUpdate(
        """
            insert into valideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
            values (?, ?, ?, ?, ? )
            """,
        filnavn,
        kravlinje.linjenummer,
        kravlinje.saksnummerNav,
        kravlinje.toString(),
        feilmelding,
    )
}
