package sokos.ske.krav.database

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
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
import java.sql.Types
import java.time.LocalDate
import java.util.UUID

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
            kotlinx.datetime.LocalDate::class -> getTimestamp(columnLabel)?.toLocalDateTime()!!.toKotlinxLocalDate()
            kotlinx.datetime.LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()!!
                .toKotlinxLocalDateTime()

            java.time.LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()
            else -> {
                logger.error("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                throw RuntimeException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}") // TODO Feilhåndtering
            }
        }

        if (null !is T && columnValue == null) {
            logger.error { "Påkrevet kolonne '$columnLabel' er null" }
            throw RuntimeException("Påkrevet kolonne '$columnLabel' er null") // TODO Feilhåndtering
        }

        return transform(columnValue as T)
    }

    fun java.time.LocalDateTime.toKotlinxLocalDateTime(): kotlinx.datetime.LocalDateTime =
        kotlinx.datetime.LocalDateTime(
            this.year,
            this.month,
            this.dayOfMonth,
            this.hour,
            this.minute,
            this.second,
            this.nano
        )

    fun java.time.LocalDateTime.toKotlinxLocalDate(): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate(
            this.year,
            this.month,
            this.dayOfMonth
        )

    fun interface Parameter {
        fun addToPreparedStatement(sp: PreparedStatement, index: Int)
    }

    fun param(value: Int?) = Parameter { sp: PreparedStatement, index: Int ->
        value?.let {
            sp.setInt(index, it)
        } ?: sp.setNull(index, Types.INTEGER)
    }

    fun param(value: Long?) = Parameter { sp: PreparedStatement, index: Int ->
        value?.let {
            sp.setLong(index, value)
        } ?: sp.setNull(index, Types.BIGINT)
    }

    fun param(value: String?) = Parameter { sp: PreparedStatement, index: Int -> sp.setString(index, value) }
    fun param(value: UUID) = Parameter { sp: PreparedStatement, index: Int -> sp.setObject(index, value) }
    fun param(value: Boolean) = Parameter { sp: PreparedStatement, index: Int -> sp.setBoolean(index, value) }
    fun param(value: BigDecimal) = Parameter { sp: PreparedStatement, index: Int -> sp.setBigDecimal(index, value) }
    fun param(value: java.time.LocalDate) =
        Parameter { sp: PreparedStatement, index: Int -> sp.setDate(index, Date.valueOf(value)) }

    fun param(value: kotlinx.datetime.LocalDate) =
        Parameter { sp: PreparedStatement, index: Int -> sp.setDate(index, Date.valueOf(value.toJavaLocalDate())) }

    fun param(value: java.time.LocalDateTime) =
        Parameter { sp: PreparedStatement, index: Int -> sp.setTimestamp(index, Timestamp.valueOf(value)) }

    fun param(value: kotlinx.datetime.LocalDateTime) =
        Parameter { sp: PreparedStatement, index: Int ->
            sp.setTimestamp(
                index,
                Timestamp.valueOf(value.toJavaLocalDateTime())
            )
        }

    fun param(value: java.sql.Array) = Parameter { sp: PreparedStatement, index: Int -> sp.setArray(index, value) }
    fun PreparedStatement.withParameters(vararg parameters: Parameter?) = apply {
        var index = 1; parameters.forEach { it?.addToPreparedStatement(this, index++) }
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

    fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }

    fun String.toJavaLocaldateOrNull(): java.time.LocalDate? =
        if (this.isNullOrEmpty()) null
        else LocalDate.parse(this)


}