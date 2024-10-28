package sokos.ske.krav

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import sokos.ske.krav.api.skeApi
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.internalNaisRoutes
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.SkeService
import java.time.LocalDate
import java.util.Timer
import java.util.TimerTask

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    val logger = KotlinLogging.logger("secureLogger")

    val applicationState = ApplicationState()

    val skeService = SkeService()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        skeApi(skeService)
    }

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    StonadsType.entries.forEach {
        Metrics.incrementKravKodeSendtMetric(it.kravKode)
    }

    val timerConfig = PropertiesConfig.TimerConfig
    if (timerConfig.useTimer) {
        Timer().schedule(
            object : TimerTask() {
                override fun run() {
                    logger.info("*** Scheduled run ${LocalDate.now()} ***")
                    runBlocking { skeService.handleNewKrav() }
                }
            },
            timerConfig.initialDelay,
            timerConfig.intervalPeriod,
        )
    }
}

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    environment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
    }

    environment.monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
    }
}

class ApplicationState(
    var ready: Boolean = true,
    var alive: Boolean = true,
)
