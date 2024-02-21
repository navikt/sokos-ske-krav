package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toKobling
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.service.ENDRE_HOVEDSTOL
import sokos.ske.krav.service.ENDRE_RENTER
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isStopp
import java.sql.Connection
import java.sql.Date
import java.time.LocalDate
import java.time.LocalDateTime

object Repository {
    fun Connection.getAllKrav(): List<KravTable> {
        return prepareStatement("""select * from krav""").executeQuery().toKrav()
    }

    fun Connection.getAllKravForStatusCheck(): List<KravTable> {
        return prepareStatement("""select * from krav where status not in (?, ?, ?)""")
            .withParameters(
                param(Status.RESKONTROFOERT.value),
                param(Status.VALIDERINGSFEIL.value),
                param(Status.KRAV_IKKE_SENDT.value),
            ).executeQuery().toKrav()
    }

    fun Connection.getAllKravForReconciliation(): List<KravTable> {
        return prepareStatement("""select * from krav where HVA SKAL VI AVSTEMME PÅ ?""")  //TODO KRITERIER FOR UTPLUKK
            .withParameters(
                param("FELT FOR UTPLUKK????")  //TODO AS ABOVE
            ).executeQuery().toKrav()
    }

    fun Connection.getAllValidationErrors(): List<KravTable> {
        return prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                param(Status.VALIDERINGSFEIL.value)
            ).executeQuery().toKrav()
    }

    fun Connection.updateSendtKrav(
        corrID: String,
        responseStatus: String
    ) {
        println("Oppdaterer endring av krav med corrID $corrID")
        prepareStatement(
            """
                update krav 
                    set dato_sendt = NOW(), 
                    dato_siste_status = NOW(),
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
                    set dato_sendt = NOW(), 
                    dato_siste_status = NOW(),
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
        searchCorrID:String,
        type: String,
        responseStatus: String
    ) {
        println("Oppdaterer endring av krav! setter corrid til ${saveCorrID} fra $searchCorrID")
        prepareStatement(
            """
                update krav 
                    set dato_sendt = NOW(), 
                    dato_siste_status = NOW(),
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


    fun Connection.insertNewKrav(
        kravidentSKE: String,
        corrID: String,
        kravLinje: KravLinje,
        kravtype: String,
        responseStatus: String
    ) {
        val now = LocalDateTime.now()
        prepareStatement(
            """
                insert into krav (
                saksnummer_nav, 
                kravidentifikator_ske, 
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
                corr_id,
                dato_sendt, 
                dato_siste_status,
                kravtype
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?)
            """.trimIndent()
        ).withParameters(
            param(kravLinje.saksNummer),
            param(kravidentSKE),
            param(kravLinje.belop),
            param(kravLinje.vedtakDato),
            param(kravLinje.gjelderID),
            param(kravLinje.periodeFOM),
            param(kravLinje.periodeTOM),
            param(kravLinje.stonadsKode),
            param(kravLinje.referanseNummerGammelSak),
            param(kravLinje.transaksjonDato),
            param(kravLinje.enhetBosted),
            param(kravLinje.enhetBehandlende),
            param(kravLinje.hjemmelKode),
            param(kravLinje.arsakKode),
            param(kravLinje.belopRente),
            param(kravLinje.fremtidigYtelse),
            param(kravLinje.utbetalDato),
            param(kravLinje.fagsystemId),
            param(responseStatus),
            param(corrID),
            param(now),
            param(now),
            param(kravtype)
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
                dato_siste_status,
                kravtype,
                corr_id 
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'${Status.KRAV_IKKE_SENDT.value}',NOW(), ?, ?)
            """.trimIndent() )

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
            prepStmt.setString(7, it.stonadsKode)
            prepStmt.setString(8, it.referanseNummerGammelSak)
            prepStmt.setString(9, it.transaksjonDato)
            prepStmt.setString(10, it.enhetBosted)
            prepStmt.setString(11, it.enhetBehandlende)
            prepStmt.setString(12, it.hjemmelKode)
            prepStmt.setString(13, it.arsakKode)
            prepStmt.setDouble(14, it.belopRente.toDouble())
            prepStmt.setString(15, it.fremtidigYtelse.toString())
            prepStmt.setString(16, it.utbetalDato)
            prepStmt.setString(17, it.fagsystemId)
            prepStmt.setString(18, type)
            prepStmt.setString(19, it.corrId)
            prepStmt.addBatch()
            if (it.isEndring()){
                prepStmt.setString(1, it.saksNummer)
                prepStmt.setDouble(2, it.belop.toDouble())
                prepStmt.setDate(3, Date.valueOf(it.vedtakDato))
                prepStmt.setString(4, it.gjelderID)
                prepStmt.setString(5, it.periodeFOM)
                prepStmt.setString(6, it.periodeTOM)
                prepStmt.setString(7, it.stonadsKode)
                prepStmt.setString(8, it.referanseNummerGammelSak)
                prepStmt.setString(9, it.transaksjonDato)
                prepStmt.setString(10, it.enhetBosted)
                prepStmt.setString(11, it.enhetBehandlende)
                prepStmt.setString(12, it.hjemmelKode)
                prepStmt.setString(13, it.arsakKode)
                prepStmt.setDouble(14, it.belopRente.toDouble())
                prepStmt.setString(15, it.fremtidigYtelse.toString())
                prepStmt.setString(16, it.utbetalDato)
                prepStmt.setString(17, it.fagsystemId)
                prepStmt.setString(18, ENDRE_RENTER)
                prepStmt.setString(19, it.corrId)
                prepStmt.addBatch()
            }
        }
        prepStmt.executeBatch()
        commit()
    }

    fun Connection.getSkeKravIdent(navref: String): String {
        val rs = prepareStatement(
            """
            select id, kravidentifikator_ske from krav
            where saksnummer_nav = ? and krav.kravtype = ? order by id desc limit 1
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



    fun Connection.updateStatus(mottakStatus: MottaksStatusResponse) {
        prepareStatement(
            """
            update krav 
            set status = ?, dato_siste_status = ?
            where kravidentifikator_ske = ?
        """.trimIndent()
        ).withParameters(
            param(mottakStatus.mottaksStatus),
            param(LocalDateTime.now()),
            param(mottakStatus.kravidentifikator)
        ).execute()
        commit()
    }

    fun Connection.saveValidationError(valideringsFeilResponse: ValideringsFeilResponse, kravidSKE: String) {
        valideringsFeilResponse.valideringsfeil.forEach {
            prepareStatement(
                """
                insert into validering (
                    kravidentifikator_ske,
                    error,
                    melding,
                    dato
                ) 
                values (?, ?, ?, ?)
            """.trimIndent()
            ).withParameters(
                param(kravidSKE),
                param(it.error),
                param(it.message),
                param(LocalDate.now())
            ).execute()
        }
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

    //skal bort
    fun Connection.insertNewKobling(ref: String, nyRef: String): String {

        prepareStatement(
            """
            insert into kobling (
            saksref_fil,
            saksref_uuid,
            dato
            ) values (?, ?, ?)
        """.trimIndent()
        ).withParameters(
            param(ref),
            param(nyRef),
            param(LocalDateTime.now())
        ).execute()
        commit()

        return nyRef
    }

    fun Connection.koblesakRef(filref: String): String {
        val rs = prepareStatement(
            """
            select distinct(saksref_uuid) from kobling
            where saksref_fil = ?
        """.trimIndent()
        ).withParameters(
            param(filref)
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("saksref_uuid")
        else ""
    }

    fun Connection.getAlleKoblinger(): List<KoblingTable> {
        return prepareStatement(
            """
            select * from kobling
        """.trimIndent()
        ).executeQuery().toKobling()
    }

    fun Connection.getDivInfo(): String {
        return prepareStatement(
            """
                select a.id as kravid, a.kravtype as kravt, a.corr_id as corrida,
                  b.corr_id as corridb, b.kravId as karvidB, b.navrequest as req
                  from krav a, feilmelding b
                  where a.id = b.kravid
            """.trimIndent()).executeQuery().toString()

    }

}

