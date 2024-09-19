package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.ValideringsfeilTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isStopp
import toFeilmelding
import toKrav
import toValideringsfeil
import java.sql.Connection
import java.sql.Date
import java.util.*

object Repository {
    fun Connection.getAllKravForStatusCheck() =
        prepareStatement("""select * from krav where status in (?, ?)""")
            .withParameters(
                param(Status.KRAV_SENDT.value),
                param(Status.MOTTATT_UNDERBEHANDLING.value),
            ).executeQuery()
            .toKrav()

    fun Connection.getAllKravForResending() =
        prepareStatement("""select * from krav where status in (?, ?, ?, ?, ?)""")
            .withParameters(
                param(Status.KRAV_IKKE_SENDT.value),
                param(Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value),
                param(Status.HTTP500_ANNEN_SERVER_FEIL.value),
                param(Status.HTTP503_UTILGJENGELIG_TJENESTE.value),
                param(Status.HTTP500_INTERN_TJENERFEIL.value),
            ).executeQuery()
            .toKrav()

    fun Connection.getAllUnsentKrav() =
        prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                param(Status.KRAV_IKKE_SENDT.value),
            ).executeQuery()
            .toKrav()

    fun Connection.getAllFeilmeldinger() = prepareStatement("""select * from feilmelding""").executeQuery().toFeilmelding()

    fun Connection.getFeilmeldingForKravId(kravId: Long): List<FeilmeldingTable> =
        prepareStatement(
            """
            select * from feilmelding
            where krav_id = ?
            """.trimIndent(),
        ).withParameters(
            param(kravId),
        ).executeQuery()
            .toFeilmelding()

    fun Connection.getValidationMessageForKravId(kravTable: KravTable): List<ValideringsfeilTable> =
        prepareStatement(
            """
            select * from valideringsfeil
            where filnavn = ? and linjenummer = ?
            """.trimIndent(),
        ).withParameters(
            param(kravTable.filnavn),
            param(kravTable.linjenummer),
        ).executeQuery()
            .toValideringsfeil()

    fun Connection.getAllKravForAvstemming() =
        prepareStatement(
            """
            select * from krav 
            where status not in ( ?, ? ) order by id
            """.trimIndent(),
        ).withParameters(
            param(Status.RESKONTROFOERT.value),
            param(Status.VALIDERINGFEIL_RAPPORTERT.value),
        ).executeQuery()
            .toKrav()

    fun Connection.getSkeKravidentifikator(navref: String): String {
        val rs =
            prepareStatement(
                """
                select min(tidspunkt_opprettet) as opprettet, kravidentifikator_ske from krav
                where (saksnummer_nav = ? or referansenummergammelsak = ?) 
                and (kravidentifikator_ske is not null and kravidentifikator_ske <> '') 
                group by kravidentifikator_ske limit 1
                """.trimIndent(),
            ).withParameters(
                param(navref),
                param(navref),
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
                where saksnummer_nav = ? and referansenummergammelsak <> saksnummer_nav
                order by id limit 1
                """.trimIndent(),
            ).withParameters(
                param(navref),
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
                param(corrID),
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
            param(responseStatus),
            param(corrID),
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
            param(responseStatus),
            param(skeKravidentifikator),
            param(corrID),
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
            param(mottakStatus),
            param(corrId),
        ).execute()
        commit()
    }

    fun Connection.updateStatusForAvstemtKravToReported(kravId: Int) {
        prepareStatement(
            """
            update krav 
            set status = ?,
            tidspunkt_siste_status = NOW()
            where id = ?
            """.trimIndent(),
        ).withParameters(
            param(Status.VALIDERINGFEIL_RAPPORTERT.value),
            param(kravId),
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
            param(skeKravident),
            param(saksnummerNav),
            param(NYTT_KRAV),
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

        kravListe.forEach {
            val type: String =
                when {
                    it.isStopp() -> STOPP_KRAV
                    it.isEndring() -> ENDRING_HOVEDSTOL
                    else -> NYTT_KRAV
                }
            prepStmt.setString(1, it.saksnummerNav)
            prepStmt.setBigDecimal(2, it.belop)
            prepStmt.setDate(3, Date.valueOf(it.vedtaksDato))
            prepStmt.setString(4, it.gjelderId)
            prepStmt.setString(5, it.periodeFOM)
            prepStmt.setString(6, it.periodeTOM)
            prepStmt.setString(7, it.kravKode)
            prepStmt.setString(8, it.referansenummerGammelSak)
            prepStmt.setString(9, it.transaksjonsDato)
            prepStmt.setString(10, it.enhetBosted)
            prepStmt.setString(11, it.enhetBehandlende)
            prepStmt.setString(12, it.kodeHjemmel)
            prepStmt.setString(13, it.kodeArsak)
            prepStmt.setDouble(14, it.belopRente.toDouble())
            prepStmt.setString(15, it.fremtidigYtelse.toString())
            prepStmt.setDate(16, Date.valueOf(it.utbetalDato))
            prepStmt.setString(17, it.fagsystemId)
            prepStmt.setString(18, it.status ?: Status.KRAV_INNLEST_FRA_FIL.value)
            prepStmt.setString(19, type)
            prepStmt.setString(20, UUID.randomUUID().toString())
            prepStmt.setString(21, filnavn)
            prepStmt.setInt(22, it.linjenummer)
            prepStmt.addBatch()
            if (type == ENDRING_HOVEDSTOL) {
                prepStmt.setString(1, it.saksnummerNav)
                prepStmt.setBigDecimal(2, it.belop)
                prepStmt.setDate(3, Date.valueOf(it.vedtaksDato))
                prepStmt.setString(4, it.gjelderId)
                prepStmt.setString(5, it.periodeFOM)
                prepStmt.setString(6, it.periodeTOM)
                prepStmt.setString(7, it.kravKode)
                prepStmt.setString(8, it.referansenummerGammelSak)
                prepStmt.setString(9, it.transaksjonsDato)
                prepStmt.setString(10, it.enhetBosted)
                prepStmt.setString(11, it.enhetBehandlende)
                prepStmt.setString(12, it.kodeHjemmel)
                prepStmt.setString(13, it.kodeArsak)
                prepStmt.setDouble(14, it.belopRente.toDouble())
                prepStmt.setString(15, it.fremtidigYtelse.toString())
                prepStmt.setDate(16, Date.valueOf(it.utbetalDato))
                prepStmt.setString(17, it.fagsystemId)
                prepStmt.setString(18, it.status ?: Status.KRAV_INNLEST_FRA_FIL.value)
                prepStmt.setString(19, ENDRING_RENTE)
                prepStmt.setString(20, UUID.randomUUID().toString())
                prepStmt.setString(21, filnavn)
                prepStmt.setInt(22, it.linjenummer)
                prepStmt.addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }

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
            param(feilmelding.kravId),
            param(feilmelding.saksnummerNav),
            param(feilmelding.kravidentifikatorSKE),
            param(feilmelding.corrId),
            param(feilmelding.error),
            param(feilmelding.melding),
            param(feilmelding.navRequest),
            param(feilmelding.skeResponse),
        ).execute()
        commit()
    }

    fun Connection.insertValidationError(
        filnavn: String,
        kravlinje: KravLinje,
        feilmelding: String,
    ) {
        prepareStatement(
            """
            insert into valideringsfeil (filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding)
            values (?, ?, ?, ?, ? )
            """.trimIndent(),
        ).withParameters(
            param(filnavn),
            param(kravlinje.linjenummer),
            param(kravlinje.saksnummerNav),
            param(kravlinje.toString()),
            param(feilmelding),
        ).execute()
        commit()
    }
}
