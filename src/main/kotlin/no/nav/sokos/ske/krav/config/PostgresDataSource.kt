package no.nav.sokos.ske.krav.config

import javax.sql.DataSource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.ske.krav.config.PropertiesConfig.postgresConfig

private val logger = KotlinLogging.logger {}

object PostgresDataSource {
    val dataSource: DataSource by lazy {
        dataSource()
    }

    fun migrate() {
        val migrationConfig =
            hikariConfig()
        dataSource(hikariConfig = migrationConfig).use { migrate(it) }
    }

    fun migrate(dataSource: DataSource) {
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

    private fun dataSource(hikariConfig: HikariConfig = hikariConfig()): HikariDataSource = HikariDataSource(hikariConfig)

    private fun hikariConfig(): HikariConfig =
        HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            when {
                !(PropertiesConfig.isLocal) -> {
                    jdbcUrl = postgresConfig.url.takeIf { it.isNotBlank() }
                        ?: "jdbc:postgresql://${postgresConfig.host}:${postgresConfig.port}/${postgresConfig.name}"
                    logger.info { "Setting up PostgreSQL" }
                }
                else -> {
                    logger.info { "Setting up local PostgreSQL" }
                    this.dataSource =
                        PGSimpleDataSource().apply {
                            user = postgresConfig.username
                            password = postgresConfig.password
                            serverNames = arrayOf(postgresConfig.host)
                            databaseName = postgresConfig.name
                            portNumbers = intArrayOf(postgresConfig.port.toInt())
                        }
                }
            }
        }
}
