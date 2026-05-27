package no.nav.sokos.ske.krav.util

import javax.sql.DataSource

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER

private val dbLogger = KotlinLogging.logger {}

fun <A> DataSource.transaction(operation: (TransactionalSession) -> A): A =
    using(sessionOf(this, returnGeneratedKey = true)) { session ->
        session.transaction { tx ->
            logError {
                operation(tx)
            }
        }
    }

private fun <A> logError(block: () -> A): A =
    runCatching {
        block()
    }.onFailure {
        dbLogger.error { "Feil i databaseoperasjon" }
        dbLogger.error(TEAM_LOGS_MARKER, it) {
            "Feil i databaseoperasjon: ${it.message}"
        }
    }.getOrThrow()
