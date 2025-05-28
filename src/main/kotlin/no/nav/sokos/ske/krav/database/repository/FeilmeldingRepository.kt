package no.nav.sokos.ske.krav.database.repository

import java.sql.Connection
import no.nav.sokos.ske.krav.database.models.FeilmeldingTable
import no.nav.sokos.ske.krav.database.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.database.repository.RepositoryExtensions.executeUpdate

object FeilmeldingRepository {
    fun Connection.getAllFeilmeldinger() = prepareStatement("""select * from feilmelding""").executeQuery().toFeilmelding()

    fun Connection.getFeilmeldingForKravId(kravId: Long): List<FeilmeldingTable> =
        executeSelect(
            """
            select * from feilmelding
            where krav_id = ?
            """,
            kravId,
        ).toFeilmelding()

    fun Connection.insertFeilmelding(feilmelding: FeilmeldingTable) =
        executeUpdate(
            """
            insert into feilmelding (
                krav_id,
                saksnummer_nav,
                kravidentifikator_ske,
                corr_id,
                error,
                melding,
                nav_request,
                ske_response
            ) 
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            feilmelding.kravId,
            feilmelding.saksnummerNav,
            feilmelding.kravidentifikatorSKE,
            feilmelding.corrId,
            feilmelding.error,
            feilmelding.melding,
            feilmelding.navRequest,
            feilmelding.skeResponse,
        )
}
