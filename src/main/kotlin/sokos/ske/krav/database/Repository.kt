package sokos.ske.krav.database

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toKobling
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


const val KRAV_SENDT = "KRAV_SENDT"
const val KONFLIKT_409 = "KONFLIKT_409"
const val VALIDERINGSFEIL_422 = "VALIDERINGSFEIL_422"

@Suppress("LongMethod")
object Repository {

    fun Connection.hentAlleKravData(): List<KravTable> {
        return prepareStatement("""select * from krav""").executeQuery().toKrav()
    }

    fun Connection.hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
        return prepareStatement("""select * from krav where status <> ? and status <> ?""")
            .withParameters(
                param(MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value),
                param(MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value)
            ).executeQuery().toKrav()
    }


    fun Connection.hentAlleKravMedValideringsfeil(): List<KravTable> {
        return prepareStatement("""select * from krav where status = ?""")
            .withParameters(
                param(MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value)
            ).executeQuery().toKrav()
    }

    fun Connection.lagreNyttKrav(
        kravidentSKE: String,
        kravLinje: KravLinje,
        kravtype: String,
        responseStatus: HttpStatusCode
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
            param(kravLinje.belop.toBigDecimal()),
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
            param(kravLinje.belopRente.toBigDecimal()),
            param(kravLinje.fremtidigYtelse.toBigDecimal()),
            param(kravLinje.utbetalDato),
            param(kravLinje.fagsystemId),
            param(
                when {
                    responseStatus.isSuccess() -> KRAV_SENDT
                    responseStatus == HttpStatusCode.Conflict -> KONFLIKT_409 //bytte ut med httpstatuscode beskrivelse, eller value?
                    responseStatus == HttpStatusCode.UnprocessableEntity -> VALIDERINGSFEIL_422 //bytte ut med httpstatuscode beskrivelse, eller value?
                    else -> "UKJENT_${responseStatus.value}" //Bare bruke httpstatuscode?
                }
            ),
            param(now),
            param(now),
            param(kravtype)
        ).execute()
        commit()
    }


    fun Connection.hentSkeKravIdent(navref: String): String {
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

    fun Connection.lagreNyKobling(ref: String): String {
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

    fun Connection.hentAlleKoblinger(): List<KoblingTable> {
        return prepareStatement(
            """
            select * from kobling
        """.trimIndent()
        ).executeQuery().toKobling()
    }

    fun Connection.oppdaterStatus(mottakStatus: MottaksStatusResponse) {
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

    fun Connection.lagreValideringsfeil(valideringsFeilResponse: ValideringsFeilResponse, kravidSKE: String) {
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
}
