package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toKobling
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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

    fun Connection.insertNewKrav(
        kravidentSKE: String,
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
                dato_sendt, 
                dato_siste_status,
                kravtype
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
            param(now),
            param(now),
            param(kravtype)
        ).execute()
        commit()
    }

    fun Connection.getSkeKravIdent(navref: String): String {
        val rs = prepareStatement(
            """
            select id, kravidentifikator_ske from krav
            where saksnummer_nav = ? order by id desc limit 1
        """.trimIndent()
        ).withParameters(
            param(navref)
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("kravidentifikator_ske")
        else ""
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

    fun Connection.saveErrorMessage(feilmelding: FeilmeldingTable) {
        prepareStatement(
            """
                insert into feilmelding (
                kravid,
                    saksnummer,
                    kravidentifikator_ske,
                    error,
				    melding,
					navRequest,
                    skeResponse,
					dato
                ) 
                values (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).withParameters(
            param(feilmelding.kravId),
            param(feilmelding.saksnummer),
            param(feilmelding.kravidentifikatorSKE),
            param(feilmelding.error),
            param(feilmelding.melding),
            param(feilmelding.navRequest),
            param(feilmelding.skeResponse),
            param(LocalDate.now())
        ).execute()
        commit()
    }

    //skal bort
    fun Connection.insertNewKobling(ref: String): String {
        val nyref = UUID.randomUUID().toString()
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
            param(nyref),
            param(LocalDateTime.now())
        ).execute()
        commit()

        return nyref
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

}

