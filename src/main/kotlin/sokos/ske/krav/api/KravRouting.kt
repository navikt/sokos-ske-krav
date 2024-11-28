package sokos.ske.krav.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import mu.KotlinLogging
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService

fun Routing.internalRoutes(
    skeService: SkeService,
    statusService: StatusService = StatusService(),
) {
    val logger = KotlinLogging.logger("secureLogger")

    route("krav") {
        get("hentNye") {
            logger.info("API Kall: Henter nye krav manuelt")
            call.respond(HttpStatusCode.OK, "Da er den i gang ${Clock.System.now()}")
            skeService.handleNewKrav()
        }
        get("hentStatus") {
            logger.info("API Kall: Oppdaterer mottaksstatus manuelt")
            call.respond(statusService.hentOgOppdaterMottaksStatus())
        }
    }
}
