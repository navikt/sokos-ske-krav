package no.nav.sokos.ske.krav.database.repository

import java.sql.Connection

import no.nav.sokos.ske.krav.database.models.FilValideringsfeilTable
import no.nav.sokos.ske.krav.database.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.database.repository.RepositoryExtensions.executeUpdate
import no.nav.sokos.ske.krav.domain.nav.KravLinje

object FilValideringsfeilRepository {
    fun Connection.getFilValideringsFeilForLinje(
        filNavn: String,
        linjeNummer: Int,
    ): List<FilValideringsfeilTable> =
        executeSelect(
            """
            select * from filvalideringsfeil
            where filnavn = ? and linjenummer = ?
            """,
            filNavn,
            linjeNummer,
        ).toValideringsfeil()

    fun Connection.getFilValideringsFeilForFil(filNavn: String): List<FilValideringsfeilTable> =
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
