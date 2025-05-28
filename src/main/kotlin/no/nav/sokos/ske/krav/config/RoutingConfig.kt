package no.nav.sokos.ske.krav.config


import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import no.nav.sokos.ske.krav.ApplicationState
import no.nav.sokos.ske.krav.api.avstemmingRoutes
import no.nav.sokos.ske.krav.api.internalRoutes
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService


@OptIn(Frontend::class)
fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
    skeService: SkeService
) {
    routing {
        internalNaisRoutes(applicationState)
        authenticate(useAuthentication, BASIC_AUTH_NAME) {
            avstemmingRoutes()
        }
        authenticate(useAuthentication, AUTHENTICATION_NAME) {
            internalRoutes(skeService)
        }
    }
}

fun Route.authenticate(
    useAuthentication: Boolean,
    authenticationProviderId: String? = null,
    block: Route.() -> Unit,
) {
    if (useAuthentication) {
        authenticate(authenticationProviderId) { block() }
    } else block()
}
