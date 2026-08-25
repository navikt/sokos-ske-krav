package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.ske.krav.api.avstemmingRoutes
import no.nav.sokos.ske.krav.service.Frontend

private val logger = mu.KotlinLogging.logger {}

@OptIn(Frontend::class)
fun Application.routingConfig(applicationState: ApplicationState) {
    routing {
        internalNaisRoutes(applicationState)
        if (PropertiesConfig.isLocal) {
            logger.info { "setter åpne ruter" }
            avstemmingRoutes()
        } else {
            authenticate {
                logger.info { "setter lukkede ruter" }
                avstemmingRoutes()
            }
        }
    }
}
