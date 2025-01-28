package sokos.ske.krav.database.repository

import sokos.ske.krav.database.models.ValideringsfeilTable
import sokos.ske.krav.database.repository.RepositoryExtensions.withParameters
import sokos.ske.krav.domain.nav.KravLinje
import java.sql.Connection

object ValideringsfeilRepository {
    fun Connection.getValideringsFeilForLinje(
        filNavn: String,
        linjeNummer: Int,
    ): List<ValideringsfeilTable> =
        prepareStatement(
            """
            select * from valideringsfeil
            where filnavn = ? and linjenummer = ?
            """.trimIndent(),
        ).withParameters(
            filNavn,
            linjeNummer,
        ).executeQuery()
            .toValideringsfeil()

    fun Connection.getValideringsFeilForFil(filNavn: String): List<ValideringsfeilTable> =
        prepareStatement(
            """
            select * from valideringsfeil
            where filnavn = ?
            """.trimIndent(),
        ).withParameters(
            filNavn,
        ).executeQuery()
            .toValideringsfeil()

    fun Connection.insertFileValideringsfeil(
        filnavn: String,
        feilmelding: String,
    ) {
        prepareStatement(
            """
            insert into valideringsfeil (filnavn, feilmelding)
            values (?, ?)
            """.trimIndent(),
        ).withParameters(
            filnavn,
            feilmelding,
        ).execute()
        commit()
    }

    fun Connection.insertLineValideringsfeil(
        filnavn: String,
        kravlinje: KravLinje?,
        feilmelding: String,
    ) {
        prepareStatement(
            """
            insert into valideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
            values (?, ?, ?, ?, ? )
            """.trimIndent(),
        ).withParameters(
            filnavn,
            kravlinje?.linjenummer,
            kravlinje?.saksnummerNav,
            kravlinje.toString(),
            feilmelding,
        ).execute()
        commit()
    }
}
