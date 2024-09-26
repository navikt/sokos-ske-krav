package sokos.ske.krav

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import sokos.ske.krav.api.skeApi
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.internalNaisRoutes
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.service.StoppKravService
import sokos.ske.krav.util.httpClient
import java.util.Timer
import java.util.TimerTask

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    val applicationState = ApplicationState()
    val tokenProvider = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient)
    val skeClient = SkeClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)
    val databaseService = DatabaseService()
    val stoppKravService = StoppKravService(skeClient, databaseService)
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(skeClient, databaseService)
    val ftpService = FtpService()

    val skeService = SkeService(skeClient, stoppKravService, endreKravService, opprettKravService, statusService, databaseService)
    val avstemmingService = AvstemmingService(databaseService)
    val timer = Timer()
    val timerConfig = PropertiesConfig.TimerConfig()

    JvmMetrics.builder().register(Metrics.registry)

    val server =
        HTTPServer
            .builder()
            .port(9400)
            .buildAndStart()

    println("HTTPServer listening on port http://localhost:" + server.port + "/metrics")

    commonConfig()
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        skeApi(skeService, statusService, avstemmingService, ftpService)
    }

    if (!PropertiesConfig.isLocal()) {
        PostgresDataSource.migrate()
    }

    if (timerConfig.useTimer) {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    println("******************Starter henting av nye filer......${Clock.System.now()}")
                    runBlocking { skeService.handleNewKrav() }
                    println("******************Henting av nye filer  ferdig......${Clock.System.now()}")
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
