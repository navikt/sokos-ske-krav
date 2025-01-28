package sokos.ske.krav.database.repository

import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.repository.RepositoryExtensions.withParameters
import java.sql.Connection

object FeilmeldingRepository {
    fun Connection.getAllFeilmeldinger() = prepareStatement("""select * from feilmelding""").executeQuery().toFeilmelding()

    fun Connection.getFeilmeldingForKravId(kravId: Long): List<FeilmeldingTable> =
        prepareStatement(
            """
            select * from feilmelding
            where krav_id = ?
            """.trimIndent(),
        ).withParameters(
            kravId,
        ).executeQuery()
            .toFeilmelding()

    fun Connection.insertFeilmelding(feilmelding: FeilmeldingTable) {
        prepareStatement(
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
            """.trimIndent(),
        ).withParameters(
            feilmelding.kravId,
            feilmelding.saksnummerNav,
            feilmelding.kravidentifikatorSKE,
            feilmelding.corrId,
            feilmelding.error,
            feilmelding.melding,
            feilmelding.navRequest,
            feilmelding.skeResponse,
        ).execute()
        commit()
    }
}
