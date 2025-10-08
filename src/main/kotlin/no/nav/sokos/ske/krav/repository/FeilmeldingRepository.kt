package no.nav.sokos.ske.krav.repository

import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.Feilmelding

object FeilmeldingRepository {
    fun getAllFeilmeldinger(session: Session): List<Feilmelding> =
        session.list(
            queryOf(
                """
                select * from feilmelding
                """.trimIndent(),
            ),
            mapToFeilmelding,
        )

    fun getFeilmeldingForKravId(
        session: Session,
        kravId: Long,
    ): List<Feilmelding> =
        session.list(
            queryOf(
                """
                select * from feilmelding
                where krav_id = ?
                """.trimIndent(),
                kravId,
            ),
            mapToFeilmelding,
        )

    fun insertFeilmelding(
        tx: TransactionalSession,
        feilmelding: Feilmelding,
    ) = tx.update(
        queryOf(
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
            feilmelding.kravId,
            feilmelding.saksnummerNav,
            feilmelding.kravidentifikatorSKE,
            feilmelding.corrId,
            feilmelding.error,
            feilmelding.melding,
            feilmelding.navRequest,
            feilmelding.skeResponse,
        ),
    )

    private val mapToFeilmelding: (Row) -> Feilmelding = { row ->
        Feilmelding(
            feilmeldingId = row.long("id"),
            kravId = row.long("krav_id"),
            corrId = row.string("corr_id"),
            saksnummerNav = row.string("saksnummer_nav"),
            kravidentifikatorSKE = row.stringOrNull("kravidentifikator_ske"),
            error = row.string("error"),
            melding = row.string("melding"),
            navRequest = row.string("nav_request"),
            skeResponse = row.string("ske_response"),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
            rapporter = row.boolean("rapporter"),
        )
    }
}
