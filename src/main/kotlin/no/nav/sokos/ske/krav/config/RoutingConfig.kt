package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.ske.krav.api.avstemmingRoutes
import no.nav.sokos.ske.krav.service.Frontend

@OptIn(Frontend::class)
fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
        authenticate {
            avstemmingRoutes()
        }
    }
}
