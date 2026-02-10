package no.nav.sokos.ske.krav

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.ske.krav.config.ApplicationState
import no.nav.sokos.ske.krav.config.PropertiesConfigNew
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.applicationLifecycleConfig
import no.nav.sokos.ske.krav.config.mergeWithEnv
import no.nav.sokos.ske.krav.service.Frontend

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = mu.KotlinLogging.logger {}

@OptIn(Frontend::class)
private fun Application.module() {
    PropertiesConfigNew.load(environment.config.mergeWithEnv())

    val useAuthentication = PropertiesConfigNew.applicationProperties.useAuthentication
    val applicationState = ApplicationState()
//    val skeService = SkeService()
//
//    commonConfig()
    applicationLifecycleConfig(applicationState)
//    securityConfig(useAuthentication)
//    routingConfig(useAuthentication, applicationState, skeService)
//
//    if (!PropertiesConfigNew.isLocal) {
//        PostgresConfig.migrate()
//    }
//
//    StonadsType.entries
//        .flatMap { it.kravKoder }
//        .distinct()
//        .forEach { kravKode ->
//            Metrics.registerKravKodeCounter(kravKode)
//        }
//
//    if (!useTimer) {
//        return
//    }
//
//    launchJob(skeService::handleNewKrav, schedulerIntervalPeriod)
//    launchJob(skeService::checkKravDateForAlert, 24.hours)
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
