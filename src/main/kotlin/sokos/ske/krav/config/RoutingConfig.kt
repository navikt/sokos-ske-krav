package sokos.ske.krav.config


import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import sokos.ske.krav.ApplicationState
import sokos.ske.krav.api.avstemmingRoutes
import sokos.ske.krav.api.internalRoutes
import sokos.ske.krav.service.Frontend
import sokos.ske.krav.service.SkeService


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
