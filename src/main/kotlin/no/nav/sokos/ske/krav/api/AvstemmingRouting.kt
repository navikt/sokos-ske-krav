package no.nav.sokos.ske.krav.api

import io.ktor.http.ContentType.Text.CSV
import io.ktor.http.encodeURLPathPart
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

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.frontend.AdminPage
import no.nav.sokos.ske.krav.frontend.FantIngenting
import no.nav.sokos.ske.krav.frontend.FantKrav
import no.nav.sokos.ske.krav.frontend.IngentingEnda
import no.nav.sokos.ske.krav.frontend.LeitEtterKravPage
import no.nav.sokos.ske.krav.frontend.RapportPage
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportType

private val logger = KotlinLogging.logger { }

@OptIn(Frontend::class)
fun Route.avstemmingRoutes(rapportService: RapportService = RapportService()) {
    staticResources("/static", "static")

    route("") {
        get { call.respondHtmlTemplate(AdminPage(call)) {} }
    }

    route("rapporter") {
        route("avstemming") {
            get {
                call.respondHtmlTemplate(RapportPage(RapportType.AVSTEMMING, call)) {
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
            post("/resend") {
                val id = call.receiveParameters()["kravid"]?.toIntOrNull()
                if (id != null) {
                    val innloggetBruker = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
                    logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker oppdaterer status til KRAV_IKKE_SENDT for krav: $id" }
                    logger.info { "Oppdaterer status til KRAV_IKKE_SENDT for krav: $id" }
                    rapportService.oppdaterStatusTilIkkeSendt(id)
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
                call.respondHtmlTemplate(RapportPage(RapportType.RESENDING, call)) {
                    title { +"Krav Som skal resendes" }
                    resendingContent { }
                }
            }
            get("/") { call.respondRedirect("/rapporter/resending") }
        }
    }
    route("/krav") {
        get("") {
            val saksnummerNav = call.request.queryParameters["saksnummerNav"].toTrimmedSaksnummerNav()
            if (saksnummerNav != null) {
                call.respondRedirect(kravLookupPath(saksnummerNav))
                return@get
            }
            call.respondHtmlTemplate(LeitEtterKravPage(IngentingEnda, call)) {
            }
        }
        get("/") { call.respondRedirect("/krav") }
        get("/{saksnummer}") {
            val saksnummer = call.parameters["saksnummer"] ?: ""
            val content =
                when (val krav = rapportService.finnKrav(saksnummer)) {
                    null -> FantIngenting(saksnummer)
                    else -> FantKrav(krav)
                }
            call.respondHtmlTemplate(LeitEtterKravPage(content, call)) {
            }
        }
    }
}

internal fun String?.toTrimmedSaksnummerNav(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun kravLookupPath(saksnummer: String): String = "/krav/${saksnummer.encodeURLPathPart()}"

internal fun shouldUseFilesystemStatic(isLocal: Boolean = PropertiesConfig.isLocal): Boolean = isLocal
