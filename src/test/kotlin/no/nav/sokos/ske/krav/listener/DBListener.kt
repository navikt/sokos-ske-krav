package no.nav.sokos.ske.krav.listener

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.util.DBUtils.transaction

private const val DOCKER_IMAGE_NAME = "postgres:latest"
private val logger = KotlinLogging.logger("secureLogger")

object DBListener : TestListener {
    private val properties = PropertiesConfig.PostgresConfig
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(DOCKER_IMAGE_NAME)).apply {
            withReuse(false)
            withUsername(properties.adminUser)
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    val dataSource: HikariDataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }
    }

    lateinit var session: Session

    override suspend fun beforeTest(testCase: TestCase) {
        session = sessionOf(dataSource)
    }

    override suspend fun afterTest(
        testCase: TestCase,
        result: TestResult,
    ) {
        session.close()
    }

    override suspend fun afterSpec(spec: Spec) {
        resetDatabase()
    }

    suspend fun resetDatabase() {
        dataSource.transaction { session ->
            session.update(queryOf("DELETE FROM FEILMELDING"))
            session.update(queryOf("DELETE FROM VALIDERINGSFEIL"))
            session.update(queryOf("DELETE FROM KRAV"))
        }
    }

    init {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${PropertiesConfig.PostgresConfig.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    fun migrate(script: String = "") {
        if (script.isNotEmpty()) {
            ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
        }
    }
}
