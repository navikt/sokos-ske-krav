package sokos.skd.poc.apis

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.SkdService
import sokos.skd.poc.readProperty

fun Application.skdApi() {
    routing {
        route("krav") {
            get("test") {
                try {
                    val skdService = SkdService()
                    skdService.sjekkOmNyFilOgSendTilSkatt(1)
                    call.respond(HttpStatusCode.OK, "Krav sendt")
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sorry feilet: ${e.message}, \n"+
                        "clientID = ${readProperty("MASKINPORTEN_CLIENT_ID", "none")} \n " +
                        "wellKnownUrl= ${readProperty("MASKINPORTEN_WELL_KNOWN_URL", "none")} \n " +
                        "jwk_kid= ${readProperty("MASKINPORTEN_CLIENT_JWK", "none")} \n " +
                        "scopes= ${readProperty("MASKINPORTEN_SCOPES", "none")} \n +" +
                        "skdurl= ${readProperty("SKD_REST_URL", "")} \n " +
                        "Stacktrace= ${e.stackTraceToString()}"
                    )
                }
            }
        }
    }
}