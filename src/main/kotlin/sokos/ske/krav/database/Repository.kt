package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isStopp
import java.sql.Connection
import java.sql.Date
import java.time.LocalDate
import java.util.*

object Repository {

    fun Connection.getAllKravForStatusCheck() =
        prepareStatement("""select * from krav where krav.kravtype = ? and status in (?, ?)""")
            .withParameters(
                param(NYTT_KRAV),
                param(Status.KRAV_SENDT.value),
                param(Status.MOTTATT_UNDERBEHANDLING.value),
            ).executeQuery().toKrav()

    fun Connection.getAllKravForResending() =
        prepareStatement("""select * from krav where status in (?, ?, ?, ?, ?)""")
            .withParameters(
                param(Status.KRAV_IKKE_SENDT.value),
                param(Status.IKKE_RESKONTROFORT_RESEND.value),
                param(Status.ANNEN_SERVER_FEIL_500.value),
                param(Status.UTILGJENGELIG_TJENESTE_503.value),
                param(Status.INTERN_TJENERFEIL_500.value)
            ).executeQuery().toKrav()

    fun Connection.getAllUnsentKrav() =
        prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                param(Status.KRAV_IKKE_SENDT.value),
            ).executeQuery().toKrav()

    fun Connection.getAllValidationErrors() =
        prepareStatement("""select * from krav where status = ? or status = ?""")
            .withParameters(
                param(Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value),
                param(Status.VALIDERINGSFEIL_422.value)
            ).executeQuery().toKrav()

    fun Connection.getAllErrorMessages() =
        prepareStatement("""select * from feilmelding""").executeQuery().toFeilmelding()

    fun Connection.getErrorMessageForKravId(kravId: Int): List<FeilmeldingTable> {
        return prepareStatement(
            """
                select * from feilmelding
                where kravid = ?
            """.trimIndent()
        ).withParameters(
            param(kravId)
        ).executeQuery().toFeilmelding()
    }

    fun Connection.getAllKravForAvstemming() =
        prepareStatement("""select * from krav 
            where status not in ( ?, ? ) order by id
        """.trimIndent()
        ).withParameters(
            param(Status.RESKONTROFOERT.value),
            param(Status.VALIDERINGFEIL_RAPPORTERT.value)
        ).executeQuery().toKrav()

    fun Connection.getSkeKravidentifikator(navref: String): String {
        val rs = prepareStatement(
            """
            select id, kravidentifikator_ske from krav
            where (saksnummer_nav = ? or referansenummergammelsak= ?) 
            and krav.kravidentifikator_ske is not null 
            order by id desc limit 1
        """.trimIndent()
        ).withParameters(
            param(navref),
            param(navref),
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("kravidentifikator_ske")
        else ""
    }

    fun Connection.getKravTableIdFromCorrelationId(corrID: String): Long {
        val rs = prepareStatement(
            """
            select id from krav
            where corr_id = ? order by id desc limit 1
        """.trimIndent()
        ).withParameters(
            param(corrID)
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("id")
        else 0
    }

    fun Connection.updateSentKrav(
        corrID: String,
        responseStatus: String
    ) {
        prepareStatement(
            """
                update krav 
                    set tidspunkt_sendt = NOW(), 
                    tidspunkt_siste_status = NOW(),
                    status = ?
                where 
                    corr_id = ?
            """.trimIndent()
        ).withParameters(
            param(responseStatus),
            param(corrID),
        ).execute()
        commit()
    }

    fun Connection.updateSentKrav(
        corrID: String,
        skeKravidentifikator: String,
        responseStatus: String
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
            """.trimIndent()
        ).withParameters(
            param(responseStatus),
            param(skeKravidentifikator),
            param(corrID),
        ).execute()
        commit()
    }

    fun Connection.updateSentKrav(
        saveCorrID: String,
        searchCorrID: String,
        type: String,
        responseStatus: String
    ) {
        prepareStatement(
            """
                update krav 
                    set tidspunkt_sendt = NOW(), 
                    tidspunkt_siste_status = NOW(),
                    status = ?,
                    corr_id = ?
                where 
                    corr_id = ? and
                    kravtype = ?
            """.trimIndent()
        ).withParameters(
            param(responseStatus),
            param(saveCorrID),
            param(searchCorrID),
            param(type)
        ).execute()
        commit()
    }

    fun Connection.updateStatus(mottakStatus: String, corrId: String) {
        prepareStatement(
            """
            update krav 
                set status = ?, 
                tidspunkt_siste_status = NOW()
            where corr_id = ?
        """.trimIndent()
        ).withParameters(
            param(mottakStatus),
            param(corrId)
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
            """.trimIndent()
        ).withParameters(
            param(Status.VALIDERINGFEIL_RAPPORTERT.value),
            param(kravId)
        ).execute()
        commit()
    }

    fun Connection.insertAllNewKrav(
        kravListe: List<KravLinje>,
    ) {
        val prepStmt = prepareStatement(
            """
                insert into krav (
                saksnummer_nav, 
                belop,
                vedtakDato,
                gjelderId,
                periodeFOM,
                periodeTOM,
                kravkode,
                referanseNummerGammelSak,
                transaksjonDato,
                enhetBosted,
                enhetBehandlende,
                kodeHjemmel,
                kodeArsak,
                belopRente,
                fremtidigYtelse,
                utbetalDato,
                fagsystemId,
                status, 
                tidspunkt_siste_status,
                kravtype,
                corr_id,
                tidspunkt_opprettet 
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(), ?, ?, NOW())
            """.trimIndent())

        kravListe.forEach() {
            val type: String = when {
                it.isStopp() -> STOPP_KRAV
                it.isEndring() -> ENDRING_HOVEDSTOL
                else -> NYTT_KRAV
            }
            prepStmt.setString(1, it.saksNummer)
            prepStmt.setBigDecimal(2, it.belop)
            prepStmt.setDate(3, Date.valueOf(it.vedtakDato))
            prepStmt.setString(4, it.gjelderID)
            prepStmt.setString(5, it.periodeFOM)
            prepStmt.setString(6, it.periodeTOM)
            prepStmt.setString(7, it.kravKode)
            prepStmt.setString(8, it.referanseNummerGammelSak)
            prepStmt.setString(9, it.transaksjonDato)
            prepStmt.setString(10, it.enhetBosted)
            prepStmt.setString(11, it.enhetBehandlende)
            prepStmt.setString(12, it.hjemmelKode)
            prepStmt.setString(13, it.arsakKode)
            prepStmt.setDouble(14, it.belopRente.toDouble())
            prepStmt.setString(15, it.fremtidigYtelse.toString())
            prepStmt.setDate(16, Date.valueOf(it.utbetalDato))
            prepStmt.setString(17, it.fagsystemId)
            prepStmt.setString(18, it.status ?: Status.KRAV_INNLEST_FRA_FIL.value)
            prepStmt.setString(19, type)
            prepStmt.setString(20, UUID.randomUUID().toString())
            prepStmt.addBatch()
            if (type == ENDRING_HOVEDSTOL) {
                prepStmt.setString(1, it.saksNummer)
                prepStmt.setBigDecimal(2, it.belop)
                prepStmt.setDate(3, Date.valueOf(it.vedtakDato))
                prepStmt.setString(4, it.gjelderID)
                prepStmt.setString(5, it.periodeFOM)
                prepStmt.setString(6, it.periodeTOM)
                prepStmt.setString(7, it.kravKode)
                prepStmt.setString(8, it.referanseNummerGammelSak)
                prepStmt.setString(9, it.transaksjonDato)
                prepStmt.setString(10, it.enhetBosted)
                prepStmt.setString(11, it.enhetBehandlende)
                prepStmt.setString(12, it.hjemmelKode)
                prepStmt.setString(13, it.arsakKode)
                prepStmt.setDouble(14, it.belopRente.toDouble())
                prepStmt.setString(15, it.fremtidigYtelse.toString())
                prepStmt.setDate(16, Date.valueOf(it.utbetalDato))
                prepStmt.setString(17, it.fagsystemId)
                prepStmt.setString(18, it.status ?: Status.KRAV_INNLEST_FRA_FIL.value)
                prepStmt.setString(19, ENDRING_RENTE)
                prepStmt.setString(20, UUID.randomUUID().toString())
                prepStmt.addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }

    fun Connection.updateEndringWithSkeKravIdentifikator(navSaksnr: String, skeKravident: String) {
        prepareStatement(
            """
                update krav 
                    set kravidentifikator_ske = ? 
                where 
                    saksnummer_nav = ? and
                    kravtype <> 'NYTT_KRAV'
            """.trimIndent()
        ).withParameters(
            param(skeKravident),
            param(navSaksnr),
        ).execute()
        commit()
    }


    fun Connection.insertErrorMessage(feilmelding: FeilmeldingTable) {
        prepareStatement(
            """
                insert into feilmelding (
                    kravid,
                    saksnummer,
                    kravidentifikator_ske,
                    corr_id,
                    error,
				    melding,
					navRequest,
                    skeResponse,
					dato
                ) 
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).withParameters(
            param(feilmelding.kravId),
            param(feilmelding.saksnummer),
            param(feilmelding.kravidentifikatorSKE),
            param(feilmelding.corrId),
            param(feilmelding.error),
            param(feilmelding.melding),
            param(feilmelding.navRequest),
            param(feilmelding.skeResponse),
            param(LocalDate.now())
        ).execute()
        commit()
    }

}

