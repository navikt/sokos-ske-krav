package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.Parameter
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime


@Suppress("TooManyFunctions")
object RepositoryExtensions {

    val logger = KotlinLogging.logger { }

    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this)
            }
        } catch (ex: SQLException) {
            logger.error { ex.message }
            throw ex
        }
    }

    inline fun <reified T : Any?> ResultSet.getColumn(
        columnLabel: String,
        transform: (T) -> T = { it },
    ): T {
        val columnValue = when (T::class) {
            Int::class -> getInt(columnLabel)
            Long::class -> getLong(columnLabel)
            Char::class -> getString(columnLabel)?.get(0)
            Double::class -> getDouble(columnLabel)
            String::class -> getString(columnLabel)?.trim()
            Boolean::class -> getBoolean(columnLabel)
            BigDecimal::class -> getBigDecimal(columnLabel)
            LocalDate::class -> getDate(columnLabel)?.toLocalDate()
            LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()

            else -> {
                logger.error("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                throw SQLException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}") // TODO Feilhåndtering
            }
        }

        if (null !is T && columnValue == null) {
            logger.error { "Påkrevet kolonne '$columnLabel' er null" }
            throw SQLException("Påkrevet kolonne '$columnLabel' er null") // TODO Feilhåndtering
        }

        return transform(columnValue as T)
    }


    fun interface Parameter {
        fun addToPreparedStatement(statement: PreparedStatement, index: Int)
    }


    fun param(value: String?) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setString(index, value) }

    fun param(value: BigDecimal) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setBigDecimal(index, value) }

    fun param(value: LocalDate) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setDate(index, Date.valueOf(value)) }

    fun param(value: LocalDateTime) =
        Parameter { statement: PreparedStatement, index: Int ->
            statement.setTimestamp(
                index,
                Timestamp.valueOf(value)
            )
        }


    fun PreparedStatement.withParameters(vararg parameters: Parameter?) = apply {
        var index = 1
        parameters.forEach { it?.addToPreparedStatement(this, index++) }
    }

    fun ResultSet.toKrav() = toList {
        KravTable(
            kravId = getColumn("id"),
            saksnummerNAV = getColumn("saksnummer_nav"),
            saksnummerSKE = getColumn("kravidentifikator_ske"),
            belop = getColumn("belop"),
            vedtakDato = getColumn("vedtakDato"),
            gjelderId = getColumn("gjelderid"),
            periodeFOM = getColumn("periodeFOM"),
            periodeTOM = getColumn("periodeTOM"),
            kravkode = getColumn("kravkode"),
            referanseNummerGammelSak = getColumn("referanseNummerGammelSak"),
            transaksjonDato = getColumn("transaksjonDato"),
            enhetBosted = getColumn("enhetBosted"),
            enhetBehandlende = getColumn("enhetBehandlende"),
            kodeHjemmel = getColumn("kodeHjemmel"),
            kodeArsak = getColumn("kodeArsak"),
            belopRente = getColumn("belopRente"),
            fremtidigYtelse = getColumn("fremtidigYtelse"),
            utbetalDato = getColumn("utbetalDato"),
            fagsystemId = getColumn("fagsystemId"),
            status = getColumn("status"),
            datoSendt = getColumn("dato_sendt"),
            datoSisteStatus = getColumn("dato_siste_status"),
            kravtype = getColumn("kravtype")
        )
    }

    fun ResultSet.toKobling() = toList {
        KoblingTable(
            id = getColumn("id"),
            saksrefFraFil = getColumn("saksref_fil"),
            saksrefUUID = getColumn("saksref_uuid"),
            dato = getColumn("dato")
        )
    }

    fun ResultSet.toOpprettInnkrevingsOppdragResponse() = toList {
        OpprettInnkrevingsOppdragResponse(
            kravidentifikator = getString("KRAVIDENTIFIKATOR")
        )
    }

    fun ResultSet.toFeilmelding() = toList {
        FeilmeldingTable(
            feilmeldingId = getColumn("id"),
            kravId = getColumn("kravId"),
            saksnummer = getColumn("saksnummer_nav"),
            kravidentifikatorSKE = getColumn("kravidentifikator_ske"),
            error = getColumn("error"),
            melding = getColumn("melding"),
            navRequest = getColumn("navRequest"),
            skeResponse = getColumn("skeResponse"),
            dato = getColumn("dato")
        )
    }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
}