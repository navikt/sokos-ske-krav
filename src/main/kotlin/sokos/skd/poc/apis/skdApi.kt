package sokos.skd.poc.apis

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.Configuration
import sokos.skd.poc.defaultHttpClient
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient

fun Application.spkApi() {
    routing {
        route("krav") {
            get("start") {
                val token = MaskinportenAccessTokenClient(Configuration.MaskinportenClientConfig(), defaultHttpClient ).hentAccessToken()
                call.respondText { "Token: $token" }
            }
        }
    }
}