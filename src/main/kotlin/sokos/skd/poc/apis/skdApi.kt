package sokos.skd.poc.apis

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.skd.poc.service.SkeService
import sokos.skd.poc.config.PropertiesConfig


fun Application.skdApi(
    skeService: SkeService,
    maskinPortenProperties: PropertiesConfig.MaskinportenClientConfig = PropertiesConfig.MaskinportenClientConfig(),
    skeProperties: PropertiesConfig.SKEConfig = PropertiesConfig.SKEConfig()
) {
    routing {
        route("krav") {

            get("testFTP"){
                val files = skeService.sjekkOmNyFtpFil()
                call.respond(HttpStatusCode.OK, files)
            }
            get("testFTPSend"){
                val responses = skeService.sendNyeFtpFilerTilSkatt()
                val codes = responses.flatMap { listOf( "${it.status.value}: ${it.bodyAsText()}") }
                call.respond(codes)
            }
            }

            get("test") {
                try {
                    skeService.sendNyeFtpFilerTilSkatt()
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
