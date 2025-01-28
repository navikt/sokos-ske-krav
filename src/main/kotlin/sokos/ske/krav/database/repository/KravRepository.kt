package sokos.ske.krav.database.repository

import sokos.ske.krav.database.repository.RepositoryExtensions.getColumn
import sokos.ske.krav.database.repository.RepositoryExtensions.withParameters
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isStopp
import java.sql.Connection
import java.util.UUID

object KravRepository {
    fun Connection.getAllKravForStatusCheck() =
        prepareStatement("""select * from krav where status in (?, ?)""")
            .withParameters(
                Status.KRAV_SENDT.value,
                Status.MOTTATT_UNDERBEHANDLING.value,
            ).executeQuery()
            .toKrav()

    fun Connection.getAllKravForResending() =
        prepareStatement("""select * from krav where status in (?, ?, ?, ?, ?)""")
            .withParameters(
                Status.KRAV_IKKE_SENDT.value,
                Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value,
                Status.HTTP500_ANNEN_SERVER_FEIL.value,
                Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                Status.HTTP500_INTERN_TJENERFEIL.value,
            ).executeQuery()
            .toKrav()

    fun Connection.getAllUnsentKrav() =
        prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                Status.KRAV_IKKE_SENDT.value,
            ).executeQuery()
            .toKrav()

    // TODO må også returnere de med valideringsfeil rapporter = true
    // Men da må valideringsfeil tabell ha krav id
    // Eventuelt kan vi bruke saksnummer_nav men må se på hvordan visningen blir
    // Og hvordan blir det da med valideringsfeil for fil?
    // Bedre kanskje å kalle denne for getAllFeilmeldingerForKrav
    // Og så gjøre noe annet for valideringsfeil
    fun Connection.getAllKravForAvstemming() =

        prepareStatement(
            """
            select k.* from krav k
            join feilmelding f on k.id=f.krav_id
            where k.status not in ( ?, ?) 
            and f.rapporter = true 
            order by k.id
            """.trimIndent(),
        ).withParameters(
            Status.RESKONTROFOERT.value,
            Status.MIGRERT.value,
        ).executeQuery()
            .toKrav()

    fun Connection.getSkeKravidentifikator(navref: String): String {
        val rs =
            prepareStatement(
                """
                select min(tidspunkt_opprettet) as opprettet, kravidentifikator_ske from krav
                where (saksnummer_nav = ? or referansenummergammelsak = ?) 
                and (kravidentifikator_ske is not null and kravidentifikator_ske != '') 
                group by kravidentifikator_ske limit 1
                """.trimIndent(),
            ).withParameters(
                navref,
                navref,
            ).executeQuery()
        return if (rs.next()) {
            rs.getColumn("kravidentifikator_ske")
        } else {
            ""
        }
    }

    fun Connection.getPreviousReferansenummer(navref: String): String {
        val rs =
            prepareStatement(
                """
                select referansenummergammelsak from krav
                where saksnummer_nav = ? and referansenummergammelsak != saksnummer_nav
                order by id limit 1
                """.trimIndent(),
            ).withParameters(
                navref,
            ).executeQuery()
        return if (rs.next()) {
            rs.getColumn("referansenummergammelsak")
        } else {
            navref
        }
    }

    fun Connection.getKravTableIdFromCorrelationId(corrID: String): Long {
        val rs =
            prepareStatement(
                """
                select id from krav
                where corr_id = ? order by id limit 1
                """.trimIndent(),
            ).withParameters(
                corrID,
            ).executeQuery()
        return if (rs.next()) {
            rs.getColumn("id")
        } else {
            0
        }
    }

    fun Connection.updateSentKrav(
        corrID: String,
        responseStatus: String,
    ) {
        prepareStatement(
            """
            update krav 
                set tidspunkt_sendt = NOW(), 
                tidspunkt_siste_status = NOW(),
                status = ?
            where 
                corr_id = ?
            """.trimIndent(),
        ).withParameters(
            responseStatus,
            corrID,
        ).execute()
        commit()
    }

    fun Connection.updateSentKrav(
        corrID: String,
        skeKravidentifikator: String,
        responseStatus: String,
    ) {
        prepareStatement(
            """
            update krav 
                set tidspunkt_sendt = NOW(), 
                tidspunkt_siste_status = NOW(),
                status = ?,
                kravidentifikator_ske = ?
            where 
                corr_id = ?
            """.trimIndent(),
        ).withParameters(
            responseStatus,
            skeKravidentifikator,
            corrID,
        ).execute()
        commit()
    }

    fun Connection.updateStatus(
        mottakStatus: String,
        corrId: String,
    ) {
        prepareStatement(
            """
            update krav 
                set status = ?, 
                tidspunkt_siste_status = NOW()
            where corr_id = ?
            """.trimIndent(),
        ).withParameters(
            mottakStatus,
            corrId,
        ).execute()
        commit()
    }

    // Todo: Må ha samme for valideringsfeil
    fun Connection.updateStatusForAvstemtKravToReported(kravId: Int) {
        prepareStatement(
            """
            update feilmelding 
            set rapporter = false
            where krav_id = ?
            """.trimIndent(),
        ).withParameters(
            kravId,
        ).execute()
        commit()
    }

    fun Connection.updateEndringWithSkeKravIdentifikator(
        saksnummerNav: String,
        skeKravident: String,
    ) {
        prepareStatement(
            """
            update krav 
                set kravidentifikator_ske = ? 
            where 
                saksnummer_nav = ? and
                kravtype <> ?
            """.trimIndent(),
        ).withParameters(
            skeKravident,
            saksnummerNav,
            NYTT_KRAV,
        ).execute()
        commit()
    }

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
                linjenummer
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?, ?, ?, ?)
                """.trimIndent(),
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
                    ).addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }
}
