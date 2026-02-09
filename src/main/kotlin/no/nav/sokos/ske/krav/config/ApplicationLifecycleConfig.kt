package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
    }

    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
    }
}

// TODO: Switch to default false?
class ApplicationState(
    var ready: Boolean = true,
    var alive: Boolean = true, // TODO: Used but never updated. Normal?
)
