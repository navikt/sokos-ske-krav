package sokos.skd.poc.apis

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.Configuration
import sokos.skd.poc.defaultHttpClient
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient

fun Application.skdApi() {
    routing {
        route("krav") {
            get("start") {
                try {
                    val token = MaskinportenAccessTokenClient(Configuration.MaskinportenClientConfig(), defaultHttpClient ).hentAccessToken()
                    call.respondText { "Token: $token" }
                } catch (e: Exception) {
                    call.respondText { "Sorry feilet: ${e.message}" }
                }
            }
            get("test"){
                call.respondText { "dette funker" }
            }
        }
    }
}