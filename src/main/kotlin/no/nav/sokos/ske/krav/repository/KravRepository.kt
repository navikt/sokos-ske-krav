package no.nav.sokos.ske.krav.repository

import java.sql.Connection
import java.util.UUID

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeUpdate
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.getColumn
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.withParameters
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.isEndring
import no.nav.sokos.ske.krav.util.isStopp

object KravRepository {
    fun Connection.getAllKravForStatusCheck() =
        executeSelect(
            """select * from krav where status in (?, ?)""",
            Status.KRAV_SENDT.value,
            Status.MOTTATT_UNDERBEHANDLING.value,
        ).toKrav()

    fun Connection.getAllKravForResending() =
        executeSelect(
            """select * from krav where status in (?, ?, ?, ?, ?)""",
            Status.KRAV_IKKE_SENDT.value,
            Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value,
            Status.HTTP500_ANNEN_SERVER_FEIL.value,
            Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
            Status.HTTP500_INTERN_TJENERFEIL.value,
        ).toKrav()

    fun Connection.getAllUnsentKrav() =
        executeSelect(
            """select * from krav where status = ?""",
            Status.KRAV_IKKE_SENDT.value,
        ).toKrav()

    fun Connection.getAllKravForAvstemming() =
        executeSelect(
            """
            select k.* from krav k
            join feilmelding f on k.id=f.krav_id
            where k.status not in ( ?, ?) 
            and f.rapporter = true 
            order by k.id
            """,
            Status.RESKONTROFOERT.value,
            Status.MIGRERT.value,
        ).toKrav()

    fun Connection.getSkeKravidentifikator(navref: String): String {
        val rs =
            executeSelect(
                """
                select min(tidspunkt_opprettet) as opprettet, kravidentifikator_ske from krav
                where (saksnummer_nav = ? or referansenummergammelsak = ?) 
                and (kravidentifikator_ske is not null and kravidentifikator_ske != '') 
                group by kravidentifikator_ske limit 1
                """,
                navref,
                navref,
            )
        return if (rs.next()) {
            rs.getColumn("kravidentifikator_ske")
        } else {
            ""
        }
    }

    fun Connection.getPreviousReferansenummer(navref: String): String {
        val rs =
            executeSelect(
                """
                select referansenummergammelsak from krav
                where saksnummer_nav = ? and referansenummergammelsak != saksnummer_nav
                order by id limit 1
                """,
                navref,
            )
        return if (rs.next()) {
            rs.getColumn("referansenummergammelsak")
        } else {
            navref
        }
    }

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

    fun Connection.updateSentKrav(
        corrID: String,
        responseStatus: String,
    ) = executeUpdate(
        """
        update krav 
            set tidspunkt_sendt = NOW(), 
            tidspunkt_siste_status = NOW(),
            status = ?
        where 
            corr_id = ?
        """,
        responseStatus,
        corrID,
    )

    fun Connection.updateSentKrav(
        corrID: String,
        skeKravidentifikator: String,
        responseStatus: String,
    ) = executeUpdate(
        """
        update krav 
            set tidspunkt_sendt = NOW(), 
            tidspunkt_siste_status = NOW(),
            status = ?,
            kravidentifikator_ske = ?
        where 
            corr_id = ?
        """,
        responseStatus,
        skeKravidentifikator,
        corrID,
    )

    fun Connection.updateStatus(
        mottakStatus: String,
        corrId: String,
    ) = executeUpdate(
        """
        update krav 
            set status = ?, 
            tidspunkt_siste_status = NOW()
        where corr_id = ?
        """,
        mottakStatus,
        corrId,
    )

    fun Connection.updateStatusForAvstemtKravToReported(kravId: Int) =
        executeUpdate(
            """
            update feilmelding 
            set rapporter = false
            where krav_id = ?
            """,
            kravId,
        )

    fun Connection.updateEndringWithSkeKravIdentifikator(
        saksnummerNav: String,
        skeKravident: String,
    ) = executeUpdate(
        """
        update krav 
            set kravidentifikator_ske = ? 
        where 
            saksnummer_nav = ? and
            kravtype <> ?
        """,
        skeKravident,
        saksnummerNav,
        NYTT_KRAV,
    )

    fun Connection.insertAllNewKrav(
        kravListe: List<KravLinje>,
        filnavn: String,
    ) {
        val prepStmt =
            prepareStatement(
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
                linjenummer,
                tilleggsfrist
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?, ?, ?, ?, ?)
                """,
            )

        kravListe.forEach { krav ->
            val type: String =
                when {
                    krav.isStopp() -> STOPP_KRAV
                    krav.isEndring() -> ENDRING_HOVEDSTOL
                    else -> NYTT_KRAV
                }

            prepStmt
                .withParameters(
                    krav.saksnummerNav,
                    krav.belop,
                    krav.vedtaksDato,
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
                    krav.belopRente,
                    krav.fremtidigYtelse.toString(),
                    krav.utbetalDato,
                    krav.fagsystemId,
                    krav.status ?: Status.KRAV_INNLEST_FRA_FIL.value,
                    type,
                    UUID.randomUUID().toString(),
                    filnavn,
                    krav.linjenummer,
                    krav.tilleggsfrist,
                ).addBatch()

            if (type == ENDRING_HOVEDSTOL) {
                prepStmt
                    .withParameters(
                        krav.saksnummerNav,
                        krav.belop,
                        krav.vedtaksDato,
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
                        krav.belopRente,
                        krav.fremtidigYtelse.toString(),
                        krav.utbetalDato,
                        krav.fagsystemId,
                        krav.status ?: Status.KRAV_INNLEST_FRA_FIL.value,
                        ENDRING_RENTE,
                        UUID.randomUUID().toString(),
                        filnavn,
                        krav.linjenummer,
                        krav.tilleggsfrist,
                    ).addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }
}
