package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.ske.krav.api.avstemmingRoutes
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService

@OptIn(Frontend::class)
fun Application.routingConfig(
    applicationState: ApplicationState,
    skeService: SkeService,
) {
    routing {
        internalNaisRoutes(applicationState)
        if (PropertiesConfig.isLocal) {
            avstemmingRoutes(skeService)
        } else {
            authenticate {
                avstemmingRoutes(skeService)
            }
        }
    }
}
