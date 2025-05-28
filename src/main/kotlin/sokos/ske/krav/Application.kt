package sokos.ske.krav

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.PropertiesConfig.TimerConfig.intervalPeriod
import sokos.ske.krav.config.PropertiesConfig.TimerConfig.useTimer
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.routingConfig
import sokos.ske.krav.config.securityConfig
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.Frontend
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.TraceUtils

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
    applicationLifecycleConfig(applicationState)
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState, skeService)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    StonadsType.entries.forEach {
        Metrics.incrementKravKodeSendtMetric(it.kravKode)
    }

    if (!useTimer) return

    launchJob(skeService::handleNewKrav, intervalPeriod)
    launchJob(skeService::checkKravDateForAlert, 24.hours)
}

private fun launchJob(
    function: suspend () -> Unit,
    delayDuration: Duration,
) {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        while (true) {
            try {
                TraceUtils.withTracerId {
                    function()
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
