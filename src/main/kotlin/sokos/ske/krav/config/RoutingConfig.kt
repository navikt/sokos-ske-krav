package sokos.ske.krav.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import sokos.ske.krav.ApplicationState
import sokos.ske.krav.api.naisApi
import sokos.ske.krav.api.skeApi
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService

fun Application.routingConfig(
	applicationState: ApplicationState,
	skeService: SkeService,
	statusService: StatusService
) {
	routing {
		naisApi({ applicationState.ready }, { applicationState.running })
		skeApi(skeService, statusService)
	}
}