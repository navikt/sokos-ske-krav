package no.nav.sokos.ske.krav.util

import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.database.PostgresDataSource

class TestContainer {
    private val properties = PropertiesConfig.PostgresConfig
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(false)
            withUsername(properties.adminUser)
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    val dataSource: HikariDataSource =
        container.toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }

    init {
        PostgresDataSource.migrate(dataSource)
    }

    fun migrate(script: String = "") {
        if (script.isNotEmpty()) loadInitScript(script)
    }

    private fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}
