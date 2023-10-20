package sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import sokos.ske.krav.ApplicationState
import sokos.ske.krav.api.naisApi
import sokos.ske.krav.api.skeApi
import sokos.ske.krav.service.SkeService

fun Application.routingConfig(
	applicationState: ApplicationState,
	skeService: SkeService,
) {
	routing {
		naisApi({ applicationState.ready }, { applicationState.running })
		skeApi(skeService)
	}
}