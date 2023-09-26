package sokos.ske.krav.database

import kotlinx.datetime.Instant
import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.Parameter
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import java.math.BigDecimal
import java.sql.*
import java.sql.Date
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

object RepositoryExtensions {

    val logger = KotlinLogging.logger {  }

    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this)
            }
        } catch (ex: SQLException) {
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
            kotlinx.datetime.LocalDateTime::class -> Instant.fromEpochSeconds(getTimestamp(columnLabel)?.toLocalDateTime()!!.toEpochSecond(
                ZoneOffset.MIN))
            LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()
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
    fun param(value: LocalDate) = Parameter { sp: PreparedStatement, index: Int -> sp.setDate(index, Date.valueOf(value)) }
    fun param(value: LocalDateTime) = Parameter { sp: PreparedStatement, index: Int -> sp.setTimestamp(index, Timestamp.valueOf(value)) }
    fun param(value: java.sql.Array) = Parameter { sp: PreparedStatement, index: Int -> sp.setArray(index, value) }

    fun PreparedStatement.withParameters(vararg parameters: Parameter?) = apply {
        var index = 1; parameters.forEach { it?.addToPreparedStatement(this, index++) }
    }

    fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }

    fun ResultSet.toOpprettInnkrevingsOppdragResponse() = toList {
        OpprettInnkrevingsOppdragResponse(
            kravidentifikator = getString("KRAVIDENTIFIKATOR")
        )
    }

}