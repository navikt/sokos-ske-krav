package no.nav.sokos.ske.krav.util

import java.time.Duration

import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.database.PostgresDataSource

private val logger = mu.KotlinLogging.logger {}

class TestContainer {
    private val properties = PropertiesConfig.PostgresConfig
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(false)
            withUsername(properties.adminUser)
            waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofSeconds(60)),
            )
            start()
            logger.info("PostgreSQL container started on port: ${getMappedPort(5432)}")
        }

    val dataSource: HikariDataSource by lazy {
        container.toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
            connectionTimeout = 30000
            initializationFailTimeout = 10000
        }
    }

    init {
        require(container.isRunning) { "PostgreSQL container is not running" }
        PostgresDataSource.migrate(dataSource)
        logger.info("PostgreSQL container migration completed")
    }

    fun migrate(script: String = "") {
        if (script.isNotEmpty()) loadInitScript(script)
    }

    private fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}
