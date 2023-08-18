package sokos.skd.poc.apis

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.Configuration
import sokos.skd.poc.defaultHttpClient
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient
import sokos.skd.poc.readProperty

fun Application.skdApi() {
    routing {
        route("krav") {
            get("start") {
                try {
                    val token = MaskinportenAccessTokenClient(Configuration.MaskinportenClientConfig(), defaultHttpClient ).hentAccessToken()
                    call.respondText { "Token: $token" }
                } catch (e: Exception) {
                    call.respondText {
                        "Sorry feilet: ${e.message} \n"+
                        "clientID = ${readProperty("MASKINPORTEN_CLIENT_ID", "none")} \n " +
                        "wellKnownUrl= ${readProperty("MASKINPORTEN_WELL_KNOWN_URL", "none")} \n " +
                        "jwk_kid= ${readProperty("MASKINPORTEN_CLIENT_JWK", "none")} \n " +
                        "scopes= ${readProperty("MASKINPORTEN_SCOPES", "none")}"
                    }
                }
            }
            get("test"){
                call.respondText { "dette funker" }
            }
        }
    }
}