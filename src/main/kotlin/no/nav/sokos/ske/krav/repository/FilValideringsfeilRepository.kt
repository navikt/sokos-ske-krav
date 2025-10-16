package no.nav.sokos.ske.krav.repository

import java.sql.Connection

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeUpdate

object FilValideringsfeilRepository {
    fun Connection.getFilValideringsFeilForLinje(
        filNavn: String,
        linjeNummer: Int,
    ): List<FilValideringsfeil> =
        executeSelect(
            """
            select * from filvalideringsfeil
            where filnavn = ? and linjenummer = ?
            """,
            filNavn,
            linjeNummer,
        ).toValideringsfeil()

    fun Connection.getFilValideringsFeilForFil(filNavn: String): List<FilValideringsfeil> =
        executeSelect(
            """
            select * from filvalideringsfeil
            where filnavn = ?
            """,
            filNavn,
        ).toValideringsfeil()

    fun Connection.insertFileValideringsfeil(
        filnavn: String,
        feilmelding: String,
    ) = executeUpdate(
        """
            insert into filvalideringsfeil (filnavn, feilmelding)
            values (?, ?)
            """,
        filnavn,
        feilmelding,
    )

    fun Connection.insertLineFilValideringsfeil(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) = executeUpdate(
        """
            insert into filvalideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
            values (?, ?, ?, ?, ? )
            """,
        filnavn,
        kravlinje.linjenummer,
        kravlinje.saksnummerNav,
        kravlinje.toString(),
        feilmelding,
    )
}
