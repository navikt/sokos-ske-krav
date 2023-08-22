package sokos.skd.poc

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import sokos.skd.poc.apis.installCommonFeatures
import sokos.skd.poc.apis.naisApi
import sokos.skd.poc.apis.skdApi
import java.util.concurrent.TimeUnit

class HttpServer(
    private val appState: ApplicationState,
    private val skdService: SkdService,
    private val configuration: Configuration,
    port: Int = 8080,
) {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appState.running = false
            this.embeddedServer.stop(2, 5, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        installCommonFeatures()
        naisApi({ appState.ready }, { appState.running })
        skdApi(skdService)
    }

    fun start() {
        appState.running = true
        embeddedServer.start(wait = true)
    }
}

