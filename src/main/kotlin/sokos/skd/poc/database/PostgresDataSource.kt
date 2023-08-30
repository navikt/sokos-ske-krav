package sokos.skd.poc.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import sokos.skd.poc.config.PropertiesConfig
import java.sql.Connection

class PostgresDataSource(private val dbConfig: PropertiesConfig.DbConfig = PropertiesConfig.DbConfig()) {
    private val dataSource: HikariDataSource = HikariDataSource(hikariConfig())

    val connection: Connection get() = dataSource.connection
    fun close() = dataSource.close()
    private fun hikariConfig() = HikariConfig().apply {
        maximumPoolSize = 1000
        isAutoCommit = true
        //connectionTestQuery = "SELECT * FROM ${dbConfig.testTable} LIMIT 1"
        jdbcUrl = dbConfig.jdbcUrl
        username = dbConfig.username
        password = dbConfig.password
    }
}