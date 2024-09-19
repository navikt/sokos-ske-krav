package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.Parameter
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {
    val logger = KotlinLogging.logger("secureLogger")

    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this)
            }
        } catch (ex: SQLException) {
            logger.error(ex.message)
            throw ex
        }
    }

    inline fun <reified T : Any?> ResultSet.getColumn(
        columnLabel: String,
        transform: (T) -> T = { it },
    ): T {
        val columnValue =
            when (T::class) {
                Int::class -> getInt(columnLabel)
                Long::class -> getLong(columnLabel)
                Char::class -> getString(columnLabel)?.get(0) ?: ' '
                Double::class -> getDouble(columnLabel)
                String::class -> getString(columnLabel)?.trim() ?: ""
                Boolean::class -> getBoolean(columnLabel)
                BigDecimal::class -> getBigDecimal(columnLabel)
                LocalDate::class -> getDate(columnLabel)?.toLocalDate()
                LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()

                else -> {
                    logger.error("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                    throw SQLException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                }
            }

        if (null !is T && columnValue == null) {
            logger.error("Påkrevet kolonne '$columnLabel' er null")
            throw SQLException("Påkrevet kolonne '$columnLabel' er null")
        }

        return transform(columnValue as T)
    }

    fun interface Parameter {
        fun addToPreparedStatement(
            statement: PreparedStatement,
            index: Int,
        )
    }

    fun param(value: Int) = Parameter { statement: PreparedStatement, index: Int -> statement.setInt(index, value) }

    fun param(value: Long) = Parameter { statement: PreparedStatement, index: Int -> statement.setLong(index, value) }

    fun param(value: String?) = Parameter { statement: PreparedStatement, index: Int -> statement.setString(index, value) }

    fun PreparedStatement.withParameters(vararg parameters: Parameter?) =
        apply {
            parameters.forEachIndexed { index, param -> param?.addToPreparedStatement(this, index + 1) }
        }

}
