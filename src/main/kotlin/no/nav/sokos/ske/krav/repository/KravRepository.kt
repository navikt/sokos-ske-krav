package no.nav.sokos.ske.krav.repository

import java.util.UUID

import com.zaxxer.hikari.HikariDataSource
import kotliquery.LoanPattern.using
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf

import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.dto.nav.isEndring
import no.nav.sokos.ske.krav.dto.nav.type

class KravRepository(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun getAllKravForStatusCheck(): List<Krav> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select * from krav where status in (?, ?)    
                    """.trimIndent(),
                    Status.KRAV_SENDT.value,
                    Status.MOTTATT_UNDERBEHANDLING.value,
                ),
                mapToKrav,
            )
        }

    fun getAllKravForResending(): List<Krav> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select * from krav where status in (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    Status.KRAV_IKKE_SENDT.value,
                    Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value,
                    Status.HTTP500_ANNEN_SERVER_FEIL.value,
                    Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                    Status.HTTP500_INTERN_TJENERFEIL.value,
                ),
                mapToKrav,
            )
        }

    fun getAllUnsentKrav(): List<Krav> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select * from krav where status = ?
                    """.trimIndent(),
                    Status.KRAV_IKKE_SENDT.value,
                ),
                mapToKrav,
            )
        }

    fun getAllKravForAvstemming(): List<Krav> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select k.* from krav k
                    join feilmelding f on k.id=f.krav_id
                    where k.status not in ( ?, ?) 
                    and f.rapporter = true 
                    order by k.id
                    """.trimIndent(),
                    Status.RESKONTROFOERT.value,
                    Status.MIGRERT.value,
                ),
                mapToKrav,
            )
        }

    fun getSkeKravidentifikator(navref: String): String =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf(
                    """
                    select min(tidspunkt_opprettet) as opprettet, kravidentifikator_ske from krav
                    where (saksnummer_nav = ? or referansenummergammelsak = ?) 
                    and (kravidentifikator_ske is not null and kravidentifikator_ske != '') 
                    group by kravidentifikator_ske limit 1
                    """.trimIndent(),
                    navref,
                    navref,
                ),
            ) { row -> row.stringOrNull("kravidentifikator_ske") } ?: ""
        }

    fun getPreviousReferansenummer(navref: String): String =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf(
                    """
                    select referansenummergammelsak from krav
                    where saksnummer_nav = ? and referansenummergammelsak != saksnummer_nav
                    order by id limit 1
                    """.trimIndent(),
                    navref,
                ),
            ) { row -> row.stringOrNull("referansenummergammelsak") } ?: navref
        }

    fun getKravTableIdFromCorrelationId(corrID: String): Long =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf(
                    """
                    select id from krav
                    where corr_id = ? order by id limit 1
                    """.trimIndent(),
                    corrID,
                ),
            ) { row -> row.longOrNull("id") ?: 0L } ?: 0L
        }

    fun updateSentKravStatus(
        corrId: String,
        responseStatus: String,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                update krav 
                    set tidspunkt_sendt = NOW(), 
                    tidspunkt_siste_status = NOW(),
                    status = ?
                where 
                    corr_id = ?
                """.trimIndent(),
                responseStatus,
                corrId,
            ),
        )
    }

    fun updateSentKravStatusMedKravIdentifikator(
        corrId: String,
        skeKravidentifikator: String,
        responseStatus: String,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                update krav 
                    set tidspunkt_sendt = NOW(), 
                    tidspunkt_siste_status = NOW(),
                    status = ?,
                    kravidentifikator_ske = ?
                where 
                    corr_id = ?
                """.trimIndent(),
                responseStatus,
                skeKravidentifikator,
                corrId,
            ),
        )
    }

    fun updateStatus(
        mottakStatus: String,
        corrId: String,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                update krav 
                    set status = ?, 
                    tidspunkt_siste_status = NOW()
                where corr_id = ?
                """.trimIndent(),
                mottakStatus,
                corrId,
            ),
        )
    }

    fun updateStatusForAvstemtKravToReported(
        kravId: Int,
        session: Session,
    ) {
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

    fun updateEndringWithSkeKravIdentifikator(
        saksnummerNav: String,
        skeKravident: String,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                update krav 
                    set kravidentifikator_ske = ? 
                where 
                    saksnummer_nav = ? and
                    kravtype <> ?
                """.trimIndent(),
                skeKravident,
                saksnummerNav,
                NYTT_KRAV,
            ),
        )
    }

    fun insertAllNewKrav(
        kravLinjeListe: List<KravLinje>,
        filnavn: String,
        session: Session,
    ) {
        session.batchPreparedStatement(
            """
            insert into krav (
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
            linjenummer
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            kravLinjeListe.flatMap { kravLinje ->
                val baseEntry =
                    listOf(
                        kravLinje.saksnummerNav,
                        kravLinje.belop,
                        kravLinje.vedtaksDato,
                        kravLinje.gjelderId,
                        kravLinje.periodeFOM,
                        kravLinje.periodeTOM,
                        kravLinje.kravKode,
                        kravLinje.referansenummerGammelSak,
                        kravLinje.transaksjonsDato,
                        kravLinje.enhetBosted,
                        kravLinje.enhetBehandlende,
                        kravLinje.kodeHjemmel,
                        kravLinje.kodeArsak,
                        kravLinje.belopRente,
                        kravLinje.fremtidigYtelse,
                        kravLinje.utbetalDato,
                        kravLinje.fagsystemId,
                        kravLinje.status ?: Status.KRAV_INNLEST_FRA_FIL.value,
                        kravLinje.type(),
                        UUID.randomUUID().toString(),
                        filnavn,
                        kravLinje.linjenummer,
                    )

                if (kravLinje.isEndring()) {
                    val renteEntry =
                        baseEntry.toMutableList().apply {
                            this[18] = ENDRING_RENTE
                        }
                    listOf(baseEntry, renteEntry)
                } else {
                    listOf(baseEntry)
                }
            },
        )
    }

    fun getAllKrav(): List<Krav> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf("select * from krav"),
                mapToKrav,
            )
        }

    private val mapToKrav: (Row) -> Krav = { row ->
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
            status = row.string("status"),
            kravtype = row.string("kravtype"),
            corrId = row.string("corr_id"),
            tidspunktSendt = row.localDateTimeOrNull("tidspunkt_sendt"),
            tidspunktSisteStatus = row.localDateTime("tidspunkt_siste_status"),
            tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
        )
    }
}
