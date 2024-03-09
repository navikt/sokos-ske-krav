package sokos.ske.krav.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import org.flywaydb.core.Flyway
import sokos.ske.krav.config.PropertiesConfig
import java.sql.Connection

private val logger = KotlinLogging.logger { }

class PostgresDataSource {
    private val postgresConfig: PropertiesConfig.PostgresConfig = PropertiesConfig.PostgresConfig()
    private val isLocal = PropertiesConfig.Configuration().profile == PropertiesConfig.Profile.LOCAL
    private var dataSource: HikariDataSource
    private val adminRole = "${postgresConfig.name}-admin"
    private val userRole = "${postgresConfig.name}-user"
    val connection: Connection get() = dataSource.connection.apply { autoCommit = false }

    constructor(dataSource: HikariDataSource) {
        this.dataSource = dataSource
    }

    constructor(){
        if (!isLocal) {
            val role = adminRole
            logger.info("Flyway db opprettes med rolle $role")
            Flyway.configure()
                .dataSource(dataSource(role))
                .initSql("""SET ROLE "$role"""")
                .load()
                .migrate()
        }
        dataSource = dataSource()
    }

    private fun dataSource(role: String = userRole) =
        if (isLocal) HikariDataSource(hikariConfig()) else createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            postgresConfig.vaultMountPath,
            role
        )
    fun close() = dataSource.close()


    private fun hikariConfig() = HikariConfig().apply {
        minimumIdle = 1
        maxLifetime = 30000
        maximumPoolSize = 4
        connectionTimeout = 30000
        isAutoCommit = false
        idleTimeout = 10000
        //connectionTestQuery = "SELECT * FROM ${dbConfig.testTable} LIMIT 1"
        jdbcUrl = postgresConfig.jdbcUrl
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        if (isLocal) {
            username = postgresConfig.username
            password = postgresConfig.password
        }
    }
}
