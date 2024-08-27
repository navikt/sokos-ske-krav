package sokos.ske.krav.util.containers

import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.database.PostgresDataSource

class TestContainer {
    private val properties = PropertiesConfig.PostgresConfig()
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName))
            .apply {
                withUsername(properties.adminUser)
                withPassword(properties.password)
                withDatabaseName(properties.name)
                withReuse(false)
                start()
            }
    val dataSource = container.toDataSource { isAutoCommit = false }

    init {
        PostgresDataSource.migrate(dataSource)
    }

    fun migrate(script: String = "") {
        if (script.isEmpty()) PostgresDataSource.migrate(dataSource)
        loadInitScript(script)
    }

    private fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}