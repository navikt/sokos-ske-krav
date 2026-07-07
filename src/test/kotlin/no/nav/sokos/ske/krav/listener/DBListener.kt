package no.nav.sokos.ske.krav.listener

import javax.sql.DataSource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotliquery.queryOf
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.transaction

object DBListener : TestListener {
    private val container by lazy {
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16.6")).apply {
            withReuse(false)
            withUsername(PropertiesConfig.postgresConfig.adminUser)
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }
    }

    val dataSource: DataSource by lazy {
        container
            .toDataSource {
                maximumPoolSize = 10
                minimumIdle = 1
                isAutoCommit = false
            }.also {
                PostgresDataSource.migrate(it)
            }
    }

    val filvalideringsFeilRepository by lazy { FilValideringsfeilRepository(dataSource) }
    val feilmeldingRepository by lazy { FeilmeldingRepository(dataSource) }
    val kravRepository by lazy { KravRepository(dataSource) }

    fun loadInitScripts(vararg names: String) {
        dataSource // Ensure Flyway migrations have run before executing init scripts
        names.forEach { name ->
            ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
        }
    }

    fun clearDB() {
        dataSource.transaction { session ->
            val tables = mutableListOf<String>()
            // Collect all public tables except Flyway history
            session.list(
                // language=SQL
                queryOf("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history'"),
            ) { rs -> tables += rs.string("tablename") }

            if (tables.isNotEmpty()) {
                session.execute(queryOf("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE"))
            }
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        clearDB()
    }

    fun JdbcDatabaseContainer<*>.toDataSource(configure: HikariConfig.() -> Unit = {}): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.minimumIdle = 0
        config.configure()
        return HikariDataSource(config)
    }
}
