package sokos.skd.poc.util

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import sokos.skd.poc.config.PropertiesConfig
import sokos.skd.poc.database.PostgresDataSource

object DatabaseTestUtils {
    val POSTGRES_TEST_IMAGE = DockerImageName.parse("postgres:9.6.12")
    val container = PostgreSQLContainer(POSTGRES_TEST_IMAGE)


    fun getDataSource(initScriptPath: String, reusable: Boolean = false) = PostgresDataSource(initContainer(initScriptPath, reusable))
    private fun initContainer(initScriptPath: String, reusable: Boolean = false): PropertiesConfig.DbConfig {
        container.apply {
            withInitScript(initScriptPath)
            println("starting container")
            withReuse(reusable)
            start()
        }

        return PropertiesConfig.DbConfig(
            host = container.host,
            port = container.firstMappedPort,
            name = container.databaseName,
            username = container.username,
            password = container.password,
            jdbcUrl = container.getJdbcUrl()
        )
    }
}