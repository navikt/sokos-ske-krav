package no.nav.sokos.ske.krav.api

import io.ktor.http.ContentType.Text.CSV
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.frontend.RapportTemplate
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportType

val logger = KotlinLogging.logger { }

@OptIn(Frontend::class)
fun Route.avstemmingRoutes(rapportService: RapportService = RapportService()) {
    staticResources("/static", "static")

    route("rapporter") {
        route("avstemming") {
            get {
                call.respondHtmlTemplate(RapportTemplate(RapportType.AVSTEMMING, call)) {
                    title { +"Innkrevingsoppdrag med feil" }
                    avstemmingContent { }
                }
            }
            post("/update") {
                val id = call.receiveParameters()["kravid"]
                if (!id.isNullOrBlank()) {
                    val innloggetBruker = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
                    logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker oppdaterer status til rapportert for krav: $id" }
                    logger.info { "Oppdaterer status til rapportert for krav: $id" }
                    rapportService.oppdaterStatusTilRapportert(id.toInt())
                }
                call.respondRedirect("/rapporter/avstemming")
            }

            post("/CSVdownload") {
                val csv = call.receiveParameters()["csv"].toString()
                call.respondText(csv, CSV)
            }
            get("/") { call.respondRedirect("/rapporter/avstemming") }
        }
        route("resending") {
            get {
                call.respondHtmlTemplate(RapportTemplate(RapportType.RESENDING, call)) {
                    title { +"Krav Som skal resendes" }
                    resendingContent { }
                }
            }
            get("/") { call.respondRedirect("/rapporter/resending") }
        }
    }
}
