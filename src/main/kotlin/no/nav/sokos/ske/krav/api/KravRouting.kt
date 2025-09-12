package no.nav.sokos.ske.krav.api

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService

@OptIn(ExperimentalTime::class)
fun Route.internalRoutes(
    skeService: SkeService,
    statusService: StatusService = StatusService(),
) {
    route("api") {
        get("hentNye") {
            call.respond(HttpStatusCode.OK, "Startet henting av nye ${Clock.System.now()}")
            skeService.behandleSkeKrav()
        }
        get("hentStatus") {
            statusService.getMottaksStatus()
            call.respond(HttpStatusCode.OK, "Startet oppdatering status ${Clock.System.now()}")
        }
    }
}
