package no.nav.sokos.ske.krav.listener

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotliquery.queryOf
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.repository.ValideringsfeilRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction

private val logger = KotlinLogging.logger("secureLogger")

object PostgresListener : TestListener {
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(true)
            withUsername(PropertiesConfig.PostgresConfig.adminUser)
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

    val kravRepository: KravRepository by lazy {
        KravRepository(PostgresListener.dataSource)
    }

    val valideringsfeilRepository: ValideringsfeilRepository by lazy {
        ValideringsfeilRepository(PostgresListener.dataSource)
    }

    val feilmeldingRepository: FeilmeldingRepository by lazy {
        FeilmeldingRepository(PostgresListener.dataSource)
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
