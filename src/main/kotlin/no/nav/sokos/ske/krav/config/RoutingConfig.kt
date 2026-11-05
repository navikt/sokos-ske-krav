package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.ske.krav.api.avstemmingRoutes
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.testconsole.TestkonsollService

@OptIn(Frontend::class)
fun Application.routingConfig(
    applicationState: ApplicationState,
    skeService: SkeService,
    testkonsollService: TestkonsollService,
) {
    routing {
        internalNaisRoutes(applicationState)
        if (PropertiesConfig.isLocal) {
            avstemmingRoutes(skeService, testkonsollService = testkonsollService)
        } else {
            authenticate {
                avstemmingRoutes(skeService, testkonsollService = testkonsollService)
            }
        }
    }
}

fun isTestkonsollEnabled(profile: Profile = PropertiesConfig.applicationProperties.profile): Boolean = profile in setOf(Profile.LOCAL, Profile.DEV, Profile.DEV_GCP)
