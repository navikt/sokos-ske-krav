package sokos.ske.krav

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import sokos.ske.krav.apis.installCommonFeatures
import sokos.ske.krav.apis.naisApi
import sokos.ske.krav.apis.skeApi
import sokos.ske.krav.service.SkeService

import java.util.concurrent.TimeUnit

class HttpServer(
    private val appState: ApplicationState,
    private val skeService: SkeService,
    port: Int = 8080,
) {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appState.running = false
            this.embeddedServer.stop(2, 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        installCommonFeatures()
        routing {
            naisApi({ appState.ready }, { appState.running })
            skeApi(skeService)
        }
    }

    fun start() {
        appState.running = true
        embeddedServer.start(wait = true)
    }
}

