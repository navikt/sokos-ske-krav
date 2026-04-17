package no.nav.sokos.ske.krav.repository

import java.sql.Date
import java.time.LocalDate
import java.util.UUID

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.isEndring
import no.nav.sokos.ske.krav.util.isStopp

object KravRepository {
    internal val mapToKrav: (Row) -> Krav = { row ->
        Krav(
            kravId = row.long("id"),
            filnavn = row.string("filnavn"),
            linjenummer = row.int("linjenummer"),
            kravidentifikatorSKE = row.string("kravidentifikator_ske"),
            saksnummerNAV = row.string("saksnummer_nav"),
            belop = row.double("belop"),
            vedtaksDato = row.localDate("vedtaksdato"),
            gjelderId = row.string("gjelder_id"),
            periodeFOM = row.string("periode_fom"),
            periodeTOM = row.string("periode_tom"),
            kravkode = row.string("kravkode"),
            referansenummerGammelSak = row.string("referansenummergammelsak"),
            transaksjonsDato = row.string("transaksjonsdato"),
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
            tilleggsfrist = row.localDateOrNull("tilleggsfrist"),
            avsender = row.string("avsender"),
        )
    }

    fun getAllKravForStatusCheck(tx: TransactionalSession): List<Krav> =
        tx.list(
            queryOf(
                "select * from krav where status in (?, ?)",
                Status.KRAV_SENDT.value,
                Status.MOTTATT_UNDERBEHANDLING.value,
            ),
            mapToKrav,
        )

    fun getAllKravForResending(tx: TransactionalSession): List<Krav> =
        tx.list(
            queryOf(
                "select * from krav where status in (?, ?, ?, ?, ?)",
                Status.KRAV_IKKE_SENDT.value,
                Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value,
                Status.HTTP500_ANNEN_SERVER_FEIL.value,
                Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                Status.HTTP500_INTERN_TJENERFEIL.value,
            ),
            mapToKrav,
        )

    fun getAllUnsentKrav(tx: TransactionalSession): List<Krav> =
        tx.list(
            queryOf(
                "select * from krav where status = ?",
                Status.KRAV_IKKE_SENDT.value,
            ),
            mapToKrav,
        )

    fun getAllKravForAvstemming(tx: TransactionalSession): List<Krav> =
        tx.list(
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
            mapToKrav,
        )

    fun getSkeKravidentifikator(
        tx: TransactionalSession,
        navref: String,
    ): String =
        tx.single(
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
        ) { row -> row.string("kravidentifikator_ske") } ?: ""

    fun getPreviousReferansenummer(
        tx: TransactionalSession,
        navref: String,
    ): String =
        tx.single(
            queryOf(
                """
                select referansenummergammelsak from krav
                where saksnummer_nav = ? and referansenummergammelsak != saksnummer_nav
                order by id limit 1
                """.trimIndent(),
                navref,
            ),
        ) { row -> row.string("referansenummergammelsak") } ?: navref

    fun getKravTableIdFromCorrelationId(
        tx: TransactionalSession,
        corrID: String,
    ): Long =
        tx.single(
            queryOf(
                """
                select id from krav
                where corr_id = ? order by id limit 1
                """.trimIndent(),
                corrID,
            ),
        ) { row -> row.longOrNull("id") ?: 0L } ?: 0L

    fun updateSentKrav(
        tx: TransactionalSession,
        corrID: String,
        responseStatus: String,
    ) = tx.update(
        queryOf(
            """
            update krav
                set tidspunkt_sendt = NOW(),
                tidspunkt_siste_status = NOW(),
                status = ?
            where corr_id = ?
            """.trimIndent(),
            responseStatus,
            corrID,
        ),
    )

    fun updateSentKrav(
        tx: TransactionalSession,
        corrID: String,
        skeKravidentifikator: String,
        responseStatus: String,
    ) = tx.update(
        queryOf(
            """
            update krav
                set tidspunkt_sendt = NOW(),
                tidspunkt_siste_status = NOW(),
                status = ?,
                kravidentifikator_ske = ?
            where corr_id = ?
            """.trimIndent(),
            responseStatus,
            skeKravidentifikator,
            corrID,
        ),
    )

    fun updateStatus(
        tx: TransactionalSession,
        mottakStatus: String,
        corrId: String,
    ) = tx.update(
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

    fun updateStatusForAvstemtKravToReported(
        tx: TransactionalSession,
        kravId: Int,
    ) = tx.update(
        queryOf(
            """
            update feilmelding
            set rapporter = false
            where krav_id = ?
            """.trimIndent(),
            kravId,
        ),
    )

    fun updateEndringWithSkeKravIdentifikator(
        tx: TransactionalSession,
        saksnummerNav: String,
        skeKravident: String,
    ) = tx.update(
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

    fun insertAllNewKrav(
        tx: TransactionalSession,
        kravListe: List<KravLinje>,
        filnavn: String,
    ) {
        val params =
            buildList {
                for (krav in kravListe) {
                    val type =
                        when {
                            krav.isStopp() -> STOPP_KRAV
                            krav.isEndring() -> ENDRING_HOVEDSTOL
                            else -> NYTT_KRAV
                        }
                    add(kravToParams(krav, type, filnavn))
                    if (type == ENDRING_HOVEDSTOL) {
                        add(kravToParams(krav, ENDRING_RENTE, filnavn))
                    }
                }
            }
        tx.batchPreparedStatement(INSERT_KRAV_SQL, params)
    }

    fun deleteOldKrav(
        tx: TransactionalSession,
        threshold: LocalDate,
    ): Int =
        tx.update(
            queryOf(
                "delete from krav where tidspunkt_opprettet < ?",
                Date.valueOf(threshold),
            ),
        )

    private fun kravToParams(
        krav: KravLinje,
        type: String,
        filnavn: String,
    ): List<Any?> =
        listOf(
            krav.saksnummerNav,
            krav.belop.toDouble(),
            Date.valueOf(krav.vedtaksDato),
            krav.gjelderId,
            krav.periodeFOM,
            krav.periodeTOM,
            krav.kravKode,
            krav.referansenummerGammelSak,
            krav.transaksjonsDato,
            krav.enhetBosted,
            krav.enhetBehandlende,
            krav.kodeHjemmel,
            krav.kodeArsak,
            krav.belopRente.toDouble(),
            krav.fremtidigYtelse.toString(),
            Date.valueOf(krav.utbetalDato),
            krav.fagsystemId,
            krav.status ?: Status.KRAV_INNLEST_FRA_FIL.value,
            type,
            UUID.randomUUID().toString(),
            filnavn,
            krav.linjenummer,
            krav.tilleggsfrist?.let { Date.valueOf(it) },
            krav.avsender,
        )

    private const val INSERT_KRAV_SQL =
        """
        insert into krav (
            saksnummer_nav, belop, vedtaksdato, gjelder_id,
            periode_fom, periode_tom, kravkode, referansenummergammelsak,
            transaksjonsdato, enhet_bosted, enhet_behandlende, kode_hjemmel,
            kode_arsak, belop_rente, fremtidig_ytelse, utbetaldato,
            fagsystem_id, status, kravtype, corr_id,
            filnavn, linjenummer, tilleggsfrist, avsender
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """
}
