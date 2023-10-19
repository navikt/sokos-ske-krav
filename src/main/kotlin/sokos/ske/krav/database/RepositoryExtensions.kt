package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.Parameter
import sokos.ske.krav.database.models.KoblingTable
import sokos.ske.krav.database.models.KravTable
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {

    val logger = KotlinLogging.logger {  }

    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this)
            }
        } catch (ex: SQLException) {
            println(ex.message)
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
            kotlinx.datetime.LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()!!.toKotlinxLocalDateTime()
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

    fun LocalDateTime.toKotlinxLocalDateTime(): kotlinx.datetime.LocalDateTime =
        kotlinx.datetime.LocalDateTime(this.year, this.month, this.dayOfMonth,this.hour, this.minute, this.second, this.nano)


    fun interface Parameter {
        fun addToPreparedStatement(sp: PreparedStatement, index: Int)
    }


    fun param(value: String?) = Parameter { sp: PreparedStatement, index: Int -> sp.setString(index, value) }
    fun param(value: LocalDate) = Parameter { sp: PreparedStatement, index: Int -> sp.setDate(index, Date.valueOf(value)) }
    fun param(value: LocalDateTime) = Parameter { sp: PreparedStatement, index: Int -> sp.setTimestamp(index, Timestamp.valueOf(value)) }


    fun PreparedStatement.withParameters(vararg parameters: Parameter?) = apply {
        var index = 1; parameters.forEach { it?.addToPreparedStatement(this, index++) }
    }
    fun ResultSet.toKrav() = toList {
        KravTable(
            krav_id = getColumn("krav_id"),
            saksnummer_nav = getColumn("saksnummer_nav"),
            saksnummer_ske = getColumn("saksnummer_ske"),
            fildata_nav = getColumn("fildata_nav"),
            jsondata_ske = getColumn("jsondata_ske"),
            status = getColumn("status"),
            dato_sendt = getColumn("dato_sendt"),
            dato_siste_status = getColumn("dato_siste_status"),
            kravtype = getColumn("kravtype")

        )
    }

    fun ResultSet.toKobling() = toList {
        KoblingTable(
            id = getColumn("id"),
            saksref_fil = getColumn("saksref_fil"),
            saksref_uuid = getColumn("saksref_uuid"),
            dato = getColumn("dato")
        )
    }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
}