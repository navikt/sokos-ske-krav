package sokos.ske.krav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.routingConfig
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.util.httpClient
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

fun main() {
    val applicationState = ApplicationState()
    val tokenProvider = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient)
    val skeClient = SkeClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)
    val stoppKravService = StoppKravService(skeClient)
    val endreKravService = EndreKravService(skeClient)
    val opprettKravService = OpprettKravService(skeClient)
    val statusService = StatusService(skeClient)
    val alarmService = AlarmService()
    val skeService = SkeService(skeClient, stoppKravService, endreKravService, opprettKravService, statusService, alarmService)

    applicationState.ready = true
    HttpServer(applicationState, skeService, statusService).start()
}

class HttpServer(
    private val applicationState: ApplicationState,
    private val skeService: SkeService,
    private val statusService: StatusService,
    port: Int = 8080,
) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationState.running = false
            this.embeddedServer.stop(gracePeriod = 2, timeout = 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        applicationModule(applicationState, skeService, statusService)
    }

    fun start() {
        applicationState.running = true
        embeddedServer.start(wait = true)
    }
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
        if (!newValue) Metrics.appStateReadyFalse.inc()
    }

    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
        if (!newValue) Metrics.appStateRunningFalse.inc()
    }
}

private fun Application.applicationModule(
    applicationState: ApplicationState,
    skeService: SkeService,
    statusService: StatusService
) {
    commonConfig()
    routingConfig(applicationState, skeService, statusService)
}