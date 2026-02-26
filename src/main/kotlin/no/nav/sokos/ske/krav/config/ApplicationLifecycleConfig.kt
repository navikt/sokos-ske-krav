package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.alive = true
    }

    monitor.subscribe(ServerReady) {
        applicationState.ready = true
    }

    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }
}

class ApplicationState(
    var ready: Boolean = false,
    var alive: Boolean = false,
)
