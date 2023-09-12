package sokos.ske.krav.util

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.database.PostgresDataSource

object DatabaseTestUtils {
    val POSTGRES_TEST_IMAGE = DockerImageName.parse("postgres:9.6.12")
    val container = PostgreSQLContainer(POSTGRES_TEST_IMAGE)


    fun getDataSource(initScriptPath: String, reusable: Boolean = false) = PostgresDataSource(initContainer(initScriptPath, reusable))
    private fun initContainer(initScriptPath: String, reusable: Boolean = false): PropertiesConfig.PostgresConfig {
        container.apply {
            withInitScript(initScriptPath)
            println("starting container")
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