package no.nav.sokos.ske.krav.util

import com.zaxxer.hikari.HikariDataSource
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER

object DBUtils {
    private val logger = KotlinLogging.logger {}

    fun <A> HikariDataSource.transaction(operation: (TransactionalSession) -> A): A =
        using(sessionOf(this, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                handleError {
                    operation(tx)
                }
            }
        }

    private fun <A> handleError(block: () -> A): A =
        runCatching {
            block()
        }.onFailure {
            logger.error("Feil i databaseoperasjon")
            logger.error(TEAM_LOGS_MARKER, "Feil i databaseoperasjon: ${it.message}")
        }.getOrThrow()
}
