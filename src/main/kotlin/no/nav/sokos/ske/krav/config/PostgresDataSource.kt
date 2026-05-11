package no.nav.sokos.ske.krav.config

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.ske.krav.config.PropertiesConfig.postgresConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

private val logger = KotlinLogging.logger {}

object PostgresDataSource {
    val dataSource: HikariDataSource by lazy {
        dataSource()
    }

    fun migrate() {
        dataSource(role = postgresConfig.adminUser).use { migrate(it) }
    }

    fun migrate(dataSource: HikariDataSource) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun dataSource(
        hikariConfig: HikariConfig = hikariConfig(),
        role: String = postgresConfig.user,
    ): HikariDataSource =
        if (PropertiesConfig.isLocal) {
            HikariDataSource(hikariConfig)
        } else {
            logger.info { "VAULT PATH: $postgresConfig.vaultMountPath" }
            logger.info { "VAULT ROLE: $role" }
            HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
                hikariConfig,
                postgresConfig.vaultMountPath,
                role,
            )
        }

    private fun hikariConfig(): HikariConfig =
        HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            dataSource =
                PGSimpleDataSource().apply {
                    if (PropertiesConfig.isLocal) {
                        user = postgresConfig.username
                        password = postgresConfig.password
                    }
                    serverNames = arrayOf(postgresConfig.host)
                    databaseName = postgresConfig.name
                    portNumbers = intArrayOf(postgresConfig.port.toInt())
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    maxLifetime = Duration.ofMinutes(30).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(30).toMillis()
                }
        }
}
