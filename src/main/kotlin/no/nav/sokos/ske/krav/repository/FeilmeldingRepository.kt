package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.util.transaction

class FeilmeldingRepository(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    val mapToFeilmelding: (Row) -> Feilmelding = { row ->
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

    fun getAllFeilmeldinger(): List<Feilmelding> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """
                    select * from feilmelding
                    """.trimIndent(),
                ),
                mapToFeilmelding,
            )
        }

    fun getFeilmeldingerForKravId(
        session: TransactionalSession,
        kravId: Long,
    ): List<Feilmelding> =
        session.list(
            queryOf(
                """select * from feilmelding where krav_id = ?""".trimIndent(),
                kravId,
            ),
            mapToFeilmelding,
        )

    fun getFeilmeldingerForKravId(kravId: Long): List<Feilmelding> =
        dataSource.transaction { session ->
            getFeilmeldingerForKravId(session, kravId)
        }

    private fun insertFeilmeldingQuery() =
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
        ) values (:krav_id, :saksnummer_nav, :kravidentifikator_ske, :corr_id, :error, :melding, :nav_request, :ske_response)
        """.trimIndent()

    private fun insertFeilmeldingNamesParams(feilmelding: Feilmelding) =
        mapOf(
            "krav_id" to feilmelding.kravId,
            "saksnummer_nav" to feilmelding.saksnummerNav,
            "kravidentifikator_ske" to feilmelding.kravidentifikatorSKE,
            "corr_id" to feilmelding.corrId,
            "error" to feilmelding.error,
            "melding" to feilmelding.melding,
            "nav_request" to feilmelding.navRequest,
            "ske_response" to feilmelding.skeResponse,
        )

    fun insertFeilmelding(feilmelding: Feilmelding) {
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    insertFeilmeldingQuery(),
                    insertFeilmeldingNamesParams(feilmelding),
                ),
            )
        }
    }

    fun insertFeilmeldinger(feilmeldinger: List<Feilmelding>) {
        dataSource.transaction { session ->
            session.batchPreparedNamedStatement(
                insertFeilmeldingQuery(),
                feilmeldinger.map { feilmelding ->
                    insertFeilmeldingNamesParams(feilmelding)
                },
            )
        }
    }

    fun updateStatusForAvstemtKravToReported(kravId: Int) {
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    update feilmelding
                        set rapporter = false
                    where krav_id = ?
                    """.trimIndent(),
                    kravId,
                ),
            )
        }
    }

    fun deleteOldFeilmeldinger(threshold: LocalDate): Int =
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    delete from feilmelding where tidspunkt_opprettet < ?
                    """.trimIndent(),
                    threshold,
                ),
            )
        }

    companion object {
        val instance: FeilmeldingRepository by lazy { FeilmeldingRepository() }
    }
}
