package no.nav.sokos.ske.krav.repository

import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.toStatus
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.transaction

class KravRepository(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
) {
    val mapToKrav: (Row) -> Krav = { row ->
        Krav(
            kravId = row.long("id"),
            filnavn = row.string("filnavn"),
            linjenummer = row.int("linjenummer"),
            saksnummerNAV = row.string("saksnummer_nav"),
            kravidentifikatorSKE = row.stringOrNull("kravidentifikator_ske") ?: "",
            belop = row.double("belop"),
            vedtaksDato = row.localDate("vedtaksDato"),
            gjelderId = row.string("gjelder_id"),
            periodeFOM = row.string("periode_fom"),
            periodeTOM = row.string("periode_tom"),
            kravkode = row.string("kravkode"),
            referansenummerGammelSak = row.string("referansenummerGammelSak"),
            transaksjonsDato = row.string("transaksjonsDato"),
            enhetBosted = row.string("enhet_bosted"),
            enhetBehandlende = row.string("enhet_behandlende"),
            kodeHjemmel = row.string("kode_hjemmel"),
            kodeArsak = row.string("kode_arsak"),
            belopRente = row.double("belop_rente"),
            fremtidigYtelse = row.double("fremtidig_ytelse"),
            utbetalDato = row.localDate("utbetaldato"),
            fagsystemId = row.string("fagsystem_id"),
            status = row.string("status").toStatus(),
            kravtype = row.string("kravtype"),
            corrId = row.string("corr_id"),
            tidspunktSendt = row.localDateTimeOrNull("tidspunkt_sendt"),
            tidspunktSisteStatus = row.localDateTime("tidspunkt_siste_status"),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
            tilleggsfrist = row.localDateOrNull("tilleggsfrist"),
            avsender = row.stringOrNull("avsender") ?: "",
        )
    }

    fun getAllKravForStatusCheck(): List<Krav> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """select * from krav where status in (?, ?)""",
                    Status.KRAV_SENDT.value,
                    Status.MOTTATT_UNDER_BEHANDLING.value,
                ),
                extractor = mapToKrav,
            )
        }

    fun getAllUnsentEndringerAndStopp(): List<Krav> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """select * from krav where status = :kravStatus and kravtype in(:rente, :hovedstol, :stopp)""",
                    mapOf(
                        "kravStatus" to Status.KRAV_IKKE_SENDT.value,
                        "rente" to ENDRING_RENTE,
                        "hovedstol" to ENDRING_HOVEDSTOL,
                        "stopp" to STOPP_KRAV,
                    ),
                ),
                extractor = mapToKrav,
            )
        }

    fun getAllKravForAvstemming(): List<Krav> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """
                    select k.* from krav k
                    join feilmelding f on k.id=f.krav_id
                    where k.status not in (?, ?)
                    and f.rapporter = true
                    order by k.id
                    """.trimIndent(),
                    Status.RESKONTROFOERT.value,
                    Status.MIGRERT.value,
                ),
                extractor = mapToKrav,
            )
        }

    fun getAllKravForResending(): List<Krav> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """select * from krav where status in (?, ?, ?, ?, ?)""",
                    Status.KRAV_IKKE_SENDT.value,
                    Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value,
                    Status.HTTP500_ANNEN_SERVER_FEIL.value,
                    Status.HTTP500_INTERN_TJENERFEIL.value,
                    Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                ),
                extractor = mapToKrav,
            )
        }

    fun getAllUnsentKrav(): List<Krav> =
        dataSource.transaction { session ->
            session.list(
                queryOf(
                    """select * from krav where status = ?""",
                    Status.KRAV_IKKE_SENDT.value,
                ),
                extractor = mapToKrav,
            )
        }

    fun getSkeKravidentifikator(navref: String): String = dataSource.transaction { session -> getSkeKravidentifikator(session, navref) }

    fun getSkeKravidentifikator(
        session: TransactionalSession,
        navref: String,
    ): String =
        session.single(
            queryOf(
                """
                select kravidentifikator_ske from krav
                where (saksnummer_nav = :saksnummer_nav or referansenummergammelsak = :referansenummergammelsak)
                and (kravidentifikator_ske is not null and kravidentifikator_ske != '')
                limit 1
                """.trimIndent(),
                mapOf(
                    "saksnummer_nav" to navref,
                    "referansenummergammelsak" to navref,
                ),
            ),
        ) { row ->
            row.stringOrNull("kravidentifikator_ske")
        } ?: ""

    fun getPreviousReferansenummer(navref: String): String =
        dataSource.transaction { session ->
            session.single(
                queryOf(
                    """
                    select referansenummergammelsak from krav
                    where saksnummer_nav = ? and referansenummergammelsak != saksnummer_nav
                    order by id
                    limit 1
                    """.trimIndent(),
                    navref,
                ),
            ) { row ->
                row.stringOrNull("referansenummergammelsak")
            } ?: navref
        }

    fun getKravTableIdFromCorrelationId(corrID: String): Long = dataSource.transaction { session -> getKravTableIdFromCorrelationId(session, corrID) }

    fun getKravTableIdFromCorrelationId(
        session: TransactionalSession,
        corrID: String,
    ): Long =
        session.single(
            queryOf(
                """
                select id from krav
                where corr_id = ? 
                order by id
                limit 1
                """.trimIndent(),
                corrID,
            ),
        ) { row ->
            row.longOrNull("id")
        } ?: 0L

    fun updateSentKrav(
        session: TransactionalSession,
        corrID: String,
        responseStatus: Status,
        skeKravidentifikator: String? = null,
    ) {
        session.update(
            queryOf(
                """
                update krav
                    set tidspunkt_sendt = now(),
                    tidspunkt_siste_status = now(),
                    status = :status,
                    kravidentifikator_ske = coalesce(:kravidentifikator_ske, kravidentifikator_ske)
                where corr_id = :corr_id
                """.trimIndent(),
                mapOf(
                    "status" to responseStatus.value,
                    "kravidentifikator_ske" to skeKravidentifikator,
                    "corr_id" to corrID,
                ),
            ),
        )
    }

    fun updateStatus(
        session: TransactionalSession,
        mottakStatus: Status,
        corrID: String,
    ) {
        session.update(
            queryOf(
                """
                update krav
                    set status = :status,
                    tidspunkt_siste_status = now()
                where corr_id = :corr_id
                """.trimIndent(),
                mapOf(
                    "status" to mottakStatus.value,
                    "corr_id" to corrID,
                ),
            ),
        )
    }

    fun updateEndringWithSkeKravIdentifikator(
        session: TransactionalSession,
        saksnummerNav: String,
        skeKravident: String,
    ) {
        session.update(
            queryOf(
                """
                update krav
                    set kravidentifikator_ske = :kravidentifikator_ske
                where saksnummer_nav = :saksnummer_nav and kravtype <> :kravtype
                """.trimIndent(),
                mapOf(
                    "kravidentifikator_ske" to skeKravident,
                    "saksnummer_nav" to saksnummerNav,
                    "kravtype" to NYTT_KRAV,
                ),
            ),
        )
    }

    private val insertKravQuery =
        """
        insert into krav(
        saksnummer_nav,
        belop,
        vedtaksdato,
        gjelder_id,
        periode_fom,
        periode_tom,
        kravkode,
        referansenummergammelsak,
        transaksjonsdato,
        enhet_bosted,
        enhet_behandlende,
        kode_hjemmel,
        kode_arsak,
        belop_rente,
        fremtidig_ytelse,
        utbetaldato,
        fagsystem_id,
        status, 
        kravtype,
        corr_id,
        filnavn,
        linjenummer,
        tilleggsfrist,
        avsender
        ) values (:saksnummer_nav, :belop, :vedtaksdato, :gjelder_id, :periode_fom, :periode_tom, :kravkode, :referansenummergammelsak, :transaksjonsdato, :enhet_bosted, :enhet_behandlende, :kode_hjemmel, :kode_arsak, :belop_rente, :fremtidig_ytelse, :utbetaldato, :fagsystem_id, :status, :kravtype, :corr_id, :filnavn, :linjenummer, :tilleggsfrist, :avsender)
        """.trimIndent()

    private fun insertKravNamesParams(
        kravLinje: KravLinje,
        kravType: String,
        filnavn: String,
    ) = mapOf(
        "saksnummer_nav" to kravLinje.saksnummerNav,
        "belop" to kravLinje.belop,
        "vedtaksdato" to kravLinje.vedtaksDato,
        "gjelder_id" to kravLinje.gjelderId,
        "periode_fom" to kravLinje.periodeFOM,
        "periode_tom" to kravLinje.periodeTOM,
        "kravkode" to kravLinje.kravKode,
        "referansenummergammelsak" to kravLinje.referansenummerGammelSak,
        "transaksjonsdato" to kravLinje.transaksjonsDato,
        "enhet_bosted" to kravLinje.enhetBosted,
        "enhet_behandlende" to kravLinje.enhetBehandlende,
        "kode_hjemmel" to kravLinje.kodeHjemmel,
        "kode_arsak" to kravLinje.kodeArsak,
        "belop_rente" to kravLinje.belopRente,
        "fremtidig_ytelse" to kravLinje.fremtidigYtelse,
        "utbetaldato" to kravLinje.utbetalDato,
        "fagsystem_id" to kravLinje.fagsystemId,
        "status" to (kravLinje.status ?: Status.KRAV_INNLEST_FRA_FIL.value),
        "kravtype" to kravType,
        "corr_id" to UUID.randomUUID().toString(),
        "filnavn" to filnavn,
        "linjenummer" to kravLinje.linjenummer,
        "tilleggsfrist" to kravLinje.tilleggsfrist,
        "avsender" to kravLinje.avsender,
    )

    fun insertAllNewKrav(
        session: TransactionalSession,
        kravLinjer: List<KravLinje>,
        filnavn: String,
    ) {
        val params = mutableListOf<Map<String, Any?>>()
        kravLinjer.forEach { kravLinje ->
            when {
                kravLinje.isStopp() -> {
                    params.add(insertKravNamesParams(kravLinje, STOPP_KRAV, filnavn))
                }
                kravLinje.isEndring() -> {
                    params.add(insertKravNamesParams(kravLinje, ENDRING_HOVEDSTOL, filnavn))
                    params.add(insertKravNamesParams(kravLinje, ENDRING_RENTE, filnavn))
                }
                else -> {
                    params.add(insertKravNamesParams(kravLinje, NYTT_KRAV, filnavn))
                }
            }
        }

        session.batchPreparedNamedStatement(
            insertKravQuery,
            params,
        )
    }

    fun deleteOldKrav(
        session: TransactionalSession,
        threshold: LocalDate,
    ): Int =
        session.update(
            queryOf(
                """delete from krav where tidspunkt_opprettet < ?""",
                threshold,
            ),
        )

    companion object {
        val instance by lazy { KravRepository() }
    }
}
