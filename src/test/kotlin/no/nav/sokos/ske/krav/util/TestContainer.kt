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

class TestContainer {
    private val properties = PropertiesConfig.PostgresConfig
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(true)
            withUsername(properties.adminUser)
            waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofSeconds(60)),
            )

            try {
                start()
                println("PostgreSQL container started on port: ${getMappedPort(5432)}")
            } catch (e: Exception) {
                println("Error starting PostgreSQL container: ${e.message}")
                throw e
            }
        }

    val dataSource: HikariDataSource by lazy {
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                println("Creating datasource (attempt $attempt/$maxAttempts)")
                return@lazy container.toDataSource {
                    maximumPoolSize = 100
                    minimumIdle = 1
                    isAutoCommit = false
                    connectionTimeout = 30000
                    initializationFailTimeout = 10000
                }
            } catch (e: Exception) {
                if (attempt >= maxAttempts) {
                    println("Failed to create datasource after $maxAttempts attempts")
                    throw e
                }
                println("Connection attempt failed. Retrying in 2 seconds...")
                Thread.sleep(2000)
            }
        }

        throw IllegalStateException("Could not create database connection")
    }

    init {
        require(container.isRunning) { "PostgreSQL container is not running" }

        try {
            PostgresDataSource.migrate(dataSource)
        } catch (e: Exception) {
            println("Database migration failed: ${e.message}")
            throw e
        }
    }

    fun migrate(script: String = "") {
        if (script.isNotEmpty()) loadInitScript(script)
    }

    private fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}
