package sokos.ske.krav

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import sokos.ske.krav.api.naisApi
import sokos.ske.krav.api.skeApi
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.Configuration
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.database.PostgresDataSource
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
    val postgresDataSource = PostgresDataSource()
    val databaseService = DatabaseService(postgresDataSource)
    val stoppKravService = StoppKravService(skeClient, databaseService)
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(skeClient, databaseService)
    val skeService =
        SkeService(skeClient, stoppKravService, endreKravService, opprettKravService, statusService, databaseService)
    val avstemmingService = AvstemmingService(databaseService)
    val configuration = Configuration()

    applicationState.ready = true
    HttpServer(applicationState, skeService, statusService, avstemmingService, configuration).start()

    postgresDataSource.close()
}

class HttpServer(
    private val applicationState: ApplicationState,
    private val skeService: SkeService,
    private val statusService: StatusService,
    private val avstemmingService: AvstemmingService,
    private val configuration: Configuration,
    port: Int = 8080,
) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationState.running = false
            this.embeddedServer.stop(gracePeriod = 2, timeout = 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        commonConfig(configuration)
        routing {
            naisApi({ applicationState.ready }, { applicationState.running })
            skeApi(skeService, statusService, avstemmingService)
        }
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

