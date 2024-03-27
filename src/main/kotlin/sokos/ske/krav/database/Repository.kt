package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRE_HOVEDSTOL
import sokos.ske.krav.service.ENDRE_RENTER
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
        prepareStatement("""select * from krav where status in (?, ?)""")
            .withParameters(
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

    fun Connection.getAllKravForValidering() =
        prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                param(Status.KRAV_INNLEST_FRA_FIL.value),
            ).executeQuery().toKrav()

    fun Connection.getAllKravNotSent() =
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

    fun Connection.getAllFeilmeldinger() =
        prepareStatement("""select * from feilmelding""").executeQuery().toFeilmelding()

    fun Connection.getFeillinjeForKravId(kravId: Int): List<FeilmeldingTable> {
        return prepareStatement(
            """
                select * from feilmelding
                where kravid = ?
            """.trimIndent()
        ).withParameters(
            param(kravId)
        ).executeQuery().toFeilmelding()
    }

    fun Connection.getAlleKravForAvstemming() =
        prepareStatement("""select * from krav 
            where status not in ( ?, ? ) order by id
        """.trimIndent()
        ).withParameters(
            param(Status.RESKONTROFOERT.value),
            param(Status.VALIDERINGFEIL_RAPPORTERT.value)
        ).executeQuery().toKrav()

    fun Connection.getSkeKravIdent(navref: String): String {
        val rs = prepareStatement(
            """
            select id, kravidentifikator_ske from krav
            where saksnummer_nav = ? and krav.kravtype = ? and krav.kravidentifikator_ske is not null 
            order by id desc limit 1
        """.trimIndent()
        ).withParameters(
            param(navref),
            param(NYTT_KRAV)
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("kravidentifikator_ske")
        else ""
    }

    fun Connection.getKravIdfromCorrId(corrID: String): Long {
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

    fun Connection.updateSendtKrav(
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

    fun Connection.updateSendtKrav(
        corrID: String,
        kravIdentifikatorSke: String,
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
            param(kravIdentifikatorSke),
            param(corrID),
        ).execute()
        commit()
    }

    fun Connection.updateSendtKrav(
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

    fun Connection.updateAvstemtKravTilRapportert(kravId: Int) {
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
                it.isEndring() -> ENDRE_HOVEDSTOL
                else -> NYTT_KRAV
            }
            prepStmt.setString(1, it.saksNummer)
            prepStmt.setDouble(2, it.belop.toDouble())
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
            if (type == ENDRE_HOVEDSTOL) {
                prepStmt.setString(1, it.saksNummer)
                prepStmt.setDouble(2, it.belop.toDouble())
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
                prepStmt.setString(19, ENDRE_RENTER)
                prepStmt.setString(20, UUID.randomUUID().toString())
                prepStmt.addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }

    fun Connection.setSkeKravIdentPaEndring(navSaksnr: String, skeKravident: String) {
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


    fun Connection.saveErrorMessage(feilmelding: FeilmeldingTable, corrID: String) {
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
            param(corrID),
            param(feilmelding.error),
            param(feilmelding.melding),
            param(feilmelding.navRequest),
            param(feilmelding.skeResponse),
            param(LocalDate.now())
        ).execute()
        commit()
    }

}

