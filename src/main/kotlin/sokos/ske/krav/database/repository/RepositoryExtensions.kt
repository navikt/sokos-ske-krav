package sokos.ske.krav.database.repository

import sokos.ske.krav.config.secureLogger
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {
    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R =
        runCatching {
            use(block)
        }.onFailure { secureLogger.error(it.message) }.getOrThrow()

    inline fun <reified T> ResultSet.getColumn(columnLabel: String): T {
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
                    secureLogger.error("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                    throw SQLException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                }
            }

        if (null !is T && columnValue == null) {
            secureLogger.error("Påkrevet kolonne '$columnLabel' er null")
            throw SQLException("Påkrevet kolonne '$columnLabel' er null")
        }

        return columnValue as T
    }

    fun PreparedStatement.withParameters(vararg parameters: Any?) =
        apply {
            parameters.forEachIndexed { index, param ->
                val idx = index + 1
                when (param) {
                    is Int -> setInt(idx, param)
                    is Long -> setLong(idx, param)
                    is String -> setString(idx, param)
                    is LocalDate -> setDate(idx, Date.valueOf(param))
                    is BigDecimal -> setDouble(idx, param.toDouble())
                }
            }
        }

    fun Connection.executeUpdate(
        query: String,
        vararg params: Any?,
    ) {
        prepareStatement(query).withParameters(*params).execute()
        commit()
    }

    fun Connection.executeSelect(
        query: String,
        vararg params: Any?,
    ): ResultSet =
        prepareStatement(query)
            .withParameters(*params)
            .executeQuery()
}
