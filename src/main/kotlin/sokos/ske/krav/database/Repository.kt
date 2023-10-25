package sokos.ske.krav.database

import io.ktor.http.*
import kotlinx.datetime.toLocalDateTime
import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.param
import sokos.ske.krav.database.RepositoryExtensions.toKobling
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.DetailLine
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


const val KRAV_SENDT = "KRAV_SENDT"
const val KONFLIKT_409 = "KONFLIKT_409"
const val VALIDERINGSFEIL_422 = "VALIDERINGSFEIL_422"

object Repository {
    private val logger = KotlinLogging.logger {}


    fun Connection.hentAlleKravData(): List<KravTable> {
        return try {
            prepareStatement("""select * from krav""").executeQuery().toKrav()
        } catch (e: Exception) {
            logger.error("exception i henting av data: ${e.message}")
            listOf()
        }
    }

	fun Connection.hentAlleKravSomIkkeErReskotrofort(): List<KravTable> {
		return try {
			prepareStatement("""select * from krav where status <> ? and status <> ?""")
				.withParameters(
					param(MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value),
					param(MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value)
				).executeQuery().toKrav()
		} catch (e: Exception) {
			logger.error { "exception i henting (status) av data: ${e.message}" }
			listOf()
		}
	}


	fun Connection.hentAlleKravMedValideringsfeil(): List<KravTable> {
		return try {
			prepareStatement("""select * from krav where status = ?""")
				.withParameters(
					param(MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value)
				).executeQuery().toKrav()
		} catch (e: Exception) {
			logger.error { "exception i henting (validering) av data: ${e.message}" }
			listOf()
		}
	}

	fun Connection.lagreNyttKrav(
        skeid: String,
        request: String,
        detailLinje: DetailLine,
        kravtype: String,
        responseStatus: HttpStatusCode
	) {
		try {
			val now = LocalDateTime.now()
			prepareStatement(
				"""
                insert into krav (
                saksnummer, 
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
                kravtype,
                filnavn
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent()
            ).withParameters(
                param(detailLinje.saksNummer),
                param(skeid),
                param(detailLinje.belop.toBigDecimal()),
                param(detailLinje.vedtakDato),
                param(detailLinje.gjelderID),
                param(detailLinje.periodeFOM),
                param(detailLinje.periodeTOM),
                param(detailLinje.kravkode),
                param(detailLinje.referanseNummerGammelSak),
                param(detailLinje.transaksjonDato),
                param(detailLinje.enhetBosted),
                param(detailLinje.enhetBehandlende),
                param(detailLinje.kodeHjemmel),
                param(detailLinje.kodeArsak),
                param(detailLinje.belopRente.toBigDecimal()),
                param(detailLinje.fremtidigYtelse.toBigDecimal()),
                param(detailLinje.utbetalDato?.toLocalDateTime()!!),
                param(detailLinje.fagsystemId),
                param(
                    when {
                        responseStatus.isSuccess()  -> KRAV_SENDT
                        responseStatus.value.equals(409) -> KONFLIKT_409
                        responseStatus.value.equals(422) -> VALIDERINGSFEIL_422
                        else -> "UKJENT_${responseStatus.value}"
                    }),
                param(now),
                param(now),
                param(kravtype)
            ).execute()
            commit()
            println("lagring av $skeid OK")
        } catch (e: Exception) {
            println("lagring av $skeid feilet")
            println("exception lagring av nytt krav: ${e.message}")
        }
    }


    fun Connection.hentSkeKravIdent(navref: String): String {
        val rs = prepareStatement("""
            select krav_id, saksnummer_ske from krav
            where saksnummer_nav = ? order by krav_id desc limit 1
        """.trimIndent()
        ).withParameters(
            param(navref)
        ).executeQuery()
        return if (rs.next())
            rs.getColumn("saksnummer_ske")
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
            where saksnummer_ske = ?
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
                    saksnummer_ske,
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
