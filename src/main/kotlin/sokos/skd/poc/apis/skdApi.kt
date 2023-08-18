package sokos.skd.poc.apis

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.SkdService
import sokos.skd.poc.readProperty

fun Application.skdApi() {
    routing {
        route("krav") {
            get("start") {
                try {
                    val skdService = SkdService()
                    skdService.runjob("1.txt")
                    call.respondText { "Yepp something went ok" }
                } catch (e: Exception) {
                    call.respondText {
                        "Sorry feilet: ${e.message}, \n"+
                        "clientID = ${readProperty("MASKINPORTEN_CLIENT_ID", "none")} \n " +
                        "wellKnownUrl= ${readProperty("MASKINPORTEN_WELL_KNOWN_URL", "none")} \n " +
                        "jwk_kid= ${readProperty("MASKINPORTEN_CLIENT_JWK", "none")} \n " +
                        "scopes= ${readProperty("MASKINPORTEN_SCOPES", "none")} \n +" +
                        "skdurl= ${readProperty("SKD_REST_URL", "")} \n " +
                        "Stacktrace= ${e.stackTraceToString()}"
                    }
                }
            }
            get("test"){
                call.respondText { "dette funker" }
            }
        }
    }
}