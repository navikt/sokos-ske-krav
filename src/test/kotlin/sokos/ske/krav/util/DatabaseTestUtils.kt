package sokos.ske.krav.util


import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.database.PostgresDataSource

object DatabaseTestUtils {
    private val container = PostgreSQLContainer(DockerImageName.parse("postgres:latest"))

    fun getDataSource(initScriptPath: String, reusable: Boolean = false): PostgresDataSource = PostgresDataSource(initContainer(initScriptPath, reusable))
    private fun initContainer(initScriptPath: String, reusable: Boolean = false): PropertiesConfig.PostgresConfig {
        container.apply {
            withInitScript(initScriptPath)
            withReuse(reusable)
            start()
        }

        return PropertiesConfig.PostgresConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            name = container.databaseName,
            username = container.username,
            password = container.password,
        )
    }

}