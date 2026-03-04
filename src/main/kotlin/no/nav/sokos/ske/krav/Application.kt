package no.nav.sokos.ske.krav

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.ske.krav.config.ApplicationState
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.PropertiesConfig.timerConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.applicationLifecycleConfig
import no.nav.sokos.ske.krav.config.commonConfig
import no.nav.sokos.ske.krav.config.mergeWithEnv
import no.nav.sokos.ske.krav.config.routingConfig
import no.nav.sokos.ske.krav.config.securityConfig
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = mu.KotlinLogging.logger {}

@OptIn(Frontend::class)
private fun Application.module() {
    PropertiesConfig.load(environment.config.mergeWithEnv())

    val useAuthentication = PropertiesConfig.applicationProperties.useAuthentication
    val applicationState = ApplicationState()
    val skeService = SkeService()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState, skeService)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    StonadsType.entries
        .flatMap { it.kravKoder }
        .distinct()
        .forEach { kravKode ->
            Metrics.registerKravKodeCounter(kravKode)
        }

    logger.info { "USE TIMER? ${timerConfig.useTimer}" }
    if (!timerConfig.useTimer) {
        return
    }

    launchJob(skeService::handleNewKrav, timerConfig.schedulerIntervalPeriod)
    launchJob(skeService::checkKravDateForAlert, 24.hours)
}

private fun CoroutineScope.launchJob(
    function: suspend () -> Unit,
    delayDuration: Duration,
) = launch {
    while (true) {
        try {
            function()
            delay(delayDuration)
        } catch (_: CancellationException) {
            logger.info { "Scheduled task cancelled" }
            break // Exit the loop on cancellation
        } catch (e: Exception) {
            logger.error(marker = TEAM_LOGS_MARKER) { "Unhandled exception in scheduled task ${e.message}" }
            delay(delayDuration / 2)
        }
    }
}
