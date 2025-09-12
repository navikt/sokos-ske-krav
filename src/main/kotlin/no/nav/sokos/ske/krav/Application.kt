package no.nav.sokos.ske.krav

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.PropertiesConfig.TimerConfig.intervalPeriod
import no.nav.sokos.ske.krav.config.PropertiesConfig.TimerConfig.useTimer
import no.nav.sokos.ske.krav.config.commonConfig
import no.nav.sokos.ske.krav.config.routingConfig
import no.nav.sokos.ske.krav.config.securityConfig
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.util.TraceUtils

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = mu.KotlinLogging.logger {}

@OptIn(Frontend::class)
private fun Application.module() {
    val useAuthentication = PropertiesConfig.Configuration().useAuthentication
    val applicationState = ApplicationState()
    val skeService = SkeService()

    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState, skeService)

    DatabaseConfig.migrate()
    StonadsType.entries.forEach {
        Metrics.incrementKravKodeSendtMetric(it.kravKode)
    }

    applicationLifecycleConfig(applicationState)

    if (!useTimer) return
    launchJob(skeService::behandleSkeKrav, intervalPeriod)
    launchJob(skeService::checkKravDateForAlert, 24.hours)
}

private fun launchJob(
    function: suspend () -> Unit,
    delayDuration: Duration,
) {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        while (true) {
            try {
                // Create a completely isolated coroutine for each execution
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    TraceUtils.withTracerId(forceNewTrace = true) {
                        function()
                    }
                }
                delay(delayDuration)
            } catch (e: Exception) {
                logger.error(e) { "Error in scheduled task: ${e.message}" }
                delay(delayDuration / 2)
            }
        }
    }
}

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
    }

    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
    }
}

class ApplicationState(
    var ready: Boolean = true,
    var alive: Boolean = true,
)
