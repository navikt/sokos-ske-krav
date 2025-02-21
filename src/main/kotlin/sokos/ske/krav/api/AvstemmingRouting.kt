package sokos.ske.krav.api

import io.ktor.http.ContentType.Text.CSV
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import sokos.ske.krav.frontend.RapportTemplate
import sokos.ske.krav.service.Frontend
import sokos.ske.krav.service.RapportService
import sokos.ske.krav.service.RapportType

@OptIn(Frontend::class)
fun Routing.avstemmingRoutes(rapportService: RapportService = RapportService()) {
    staticResources("/static", "static")

    route("rapporter") {
        route("avstemming") {
            get {
                call.respondHtmlTemplate(RapportTemplate(RapportType.AVSTEMMING)) {
                    title { +"Innkrevingsoppdrag med feil" }
                    avstemmingContent { }
                }
            }
            post("/update") {
                val id = call.receiveParameters()["kravid"]
                println("skal oppdatere $id")
                if (!id.isNullOrBlank()) rapportService.oppdaterStatusTilRapportert(id.toInt())
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
                call.respondHtmlTemplate(RapportTemplate(RapportType.RESENDING)) {
                    title { +"Krav Som skal resendes" }
                    resendingContent { }
                }
            }
            get("/") { call.respondRedirect("/rapporter/resending") }
        }
    }
}
