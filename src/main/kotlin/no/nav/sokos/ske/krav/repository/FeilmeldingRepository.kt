package no.nav.sokos.ske.krav.repository

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.Feilmelding

class FeilmeldingRepository(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun getAllFeilmeldinger(): List<Feilmelding> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select * from feilmelding
                    """.trimIndent(),
                ),
                mapToFeilmelding,
            )
        }

    fun getFeilmeldingForKravId(kravId: Long): List<Feilmelding> =
        using(sessionOf(dataSource)) { session ->
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
        }

    fun insertFeilmeldinger(
        feilmeldinger: List<Feilmelding>,
        session: Session,
    ) = session.batchPreparedStatement(
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
        ) values (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        feilmeldinger.map { feilmelding ->
            listOf(
                feilmelding.kravId,
                feilmelding.saksnummerNav,
                feilmelding.kravidentifikatorSKE,
                feilmelding.corrId,
                feilmelding.error,
                feilmelding.melding,
                feilmelding.navRequest,
                feilmelding.skeResponse,
            )
        },
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
