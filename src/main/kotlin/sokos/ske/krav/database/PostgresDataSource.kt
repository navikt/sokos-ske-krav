package sokos.ske.krav.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import org.flywaydb.core.Flyway
import sokos.ske.krav.config.PropertiesConfig
import java.sql.Connection

private val logger = KotlinLogging.logger { }

class PostgresDataSource(private val postgresConfig: PropertiesConfig.PostgresConfig = PropertiesConfig.PostgresConfig()) {

    private val dataSource: HikariDataSource

    private val adminRole = if (isLocal()) postgresConfig.username else "${postgresConfig.name}-admin"
    private val userRole = "${postgresConfig.name}-user"
    val connection: Connection get() = dataSource.connection.apply { autoCommit = false }
    fun close() = dataSource.close()

    init {
        val role = adminRole
        logger.info { "Flyway migration opprettes med rolle $role" }
        Flyway.configure()
            .dataSource(dataSource(role))
            .initSql("""SET ROLE "$role"""")
            .load()
            .migrate()

        dataSource = dataSource()
    }

    private fun dataSource(role: String = userRole) =
        if (isLocal()) HikariDataSource(hikariConfig())
        else createHikariDataSourceWithVaultIntegration(hikariConfig(), postgresConfig.vaultMountPath, role)

    private fun hikariConfig() = HikariConfig().apply {
        maximumPoolSize = 1000
        isAutoCommit = true
        //connectionTestQuery = "SELECT * FROM ${dbConfig.testTable} LIMIT 1"
        jdbcUrl = postgresConfig.jdbcUrl
        if (isLocal()) {
            username = postgresConfig.username
            password = postgresConfig.password
        }
    }
    private fun isLocal() = postgresConfig.vaultMountPath.isBlank()
}
