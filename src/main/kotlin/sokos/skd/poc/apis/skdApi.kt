package sokos.skd.poc.apis

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.service.SkdService
import sokos.skd.poc.config.PropertiesConfig


fun Application.skdApi(
    skdService: SkdService,
    maskinPortenProperties: PropertiesConfig.MaskinportenClientConfig = PropertiesConfig.MaskinportenClientConfig(),
    skeProperties: PropertiesConfig.SKEConfig = PropertiesConfig.SKEConfig()
) {
    routing {
        route("krav") {

            get("testFTP"){
                val files = skdService.sjekkOmNyFtpFil()
                call.respond(HttpStatusCode.OK, files)
            }
            get("testFTPSend"){
                println("kaller api")
                val responses = skdService.sendNyeFtpFilerTilSkatt()
                println("responses: $responses")
                call.respond(HttpStatusCode.OK, responses)
            }

            get("test") {
                try {
                    skdService.sjekkOmNyFilOgSendTilSkatt(1)
                    call.respond(HttpStatusCode.OK, "Krav sendt")
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sorry feilet: ${e.message}, \n"+
                        "clientID = ${maskinPortenProperties.clientId} \n " +
                        "wellKnownUrl= ${maskinPortenProperties.authorityEndpoint} \n " +
                        "jwk_kid= ${maskinPortenProperties.rsaKey} \n " +

                        "scopes= ${maskinPortenProperties.scopes} \n +" +
                        "skdurl= ${skeProperties.skeRestUrl} \n " +
                        "Stacktrace= ${e.stackTraceToString()}"
                    )
                }
            }
        }
    }
}