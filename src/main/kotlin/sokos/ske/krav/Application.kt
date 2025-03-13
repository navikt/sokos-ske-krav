package sokos.ske.krav

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sokos.ske.krav.api.avstemmingRoutes
import sokos.ske.krav.api.internalRoutes
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.PropertiesConfig.TimerConfig.intervalPeriod
import sokos.ske.krav.config.PropertiesConfig.TimerConfig.useTimer
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.internalNaisRoutes
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.Frontend
import sokos.ske.krav.service.SkeService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

@OptIn(Frontend::class)
private fun Application.module() {
    val applicationState = ApplicationState()
    val skeService = SkeService()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        internalRoutes(skeService)
        avstemmingRoutes()
    }

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    StonadsType.entries.forEach {
        Metrics.incrementKravKodeSendtMetric(it.kravKode)
    }

    if (!useTimer) return

    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        while (true) {
            try {
                skeService.handleNewKrav()
                delay(intervalPeriod)
            } catch (e: Exception) {
                println("Error in scheduled task: ${e.message}")
                delay(intervalPeriod / 2)
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
