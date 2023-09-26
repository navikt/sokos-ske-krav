package sokos.ske.krav.apis

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.service.SkeService


fun Routing.skeApi(
    skeService: SkeService,
    maskinPortenProperties: PropertiesConfig.MaskinportenClientConfig = PropertiesConfig.MaskinportenClientConfig(),
    skeProperties: PropertiesConfig.SKEConfig = PropertiesConfig.SKEConfig()
) {

        route("krav") {
            get("testresp"){
                skeService.testResponse()
                call.respond(HttpStatusCode.OK)
            }

            get("testFTPSend") {
                println("kaller ftp send")
                val responses = skeService.sendNyeFtpFilerTilSkatt()

                val okCodes = responses.filter { it.status.isSuccess() }
                val failedCodes = responses.filter { !it.status.isSuccess() }
                call.respond(HttpStatusCode.OK, "OK: ${okCodes.size}. Feilet: ${failedCodes.size}")
            }


            get("test") {
                println("kaller test")
                try {
                    skeService.sendNyeFtpFilerTilSkatt()
                    call.respond(HttpStatusCode.OK, "Krav sendt")
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sorry feilet: ${e.message}, \n" +
                                "clientID = ${maskinPortenProperties.clientId} \n " +
                                "wellKnownUrl= ${maskinPortenProperties.authorityEndpoint} \n " +
                                "jwk_kid= ${maskinPortenProperties.rsaKey} \n " +

                                "scopes= ${maskinPortenProperties.scopes} \n +" +
                                "skeurl= ${skeProperties.skeRestUrl} \n " +
                                "Stacktrace= ${e.stackTraceToString()}"
                    )
                }
            }
            get("status") {
                println("kaller mottaksstatus")
                try {
                    call.respond(skeService.hentOgOppdaterMottaksStatus())
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sorry feilet: ${e.message}, \n" +
                                "clientID = ${maskinPortenProperties.clientId} \n " +
                                "wellKnownUrl= ${maskinPortenProperties.authorityEndpoint} \n " +
                                "jwk_kid= ${maskinPortenProperties.rsaKey} \n " +

                                "scopes= ${maskinPortenProperties.scopes} \n +" +
                                "skeurl= ${skeProperties.skeRestUrl} \n " +
                                "Stacktrace= ${e.stackTraceToString()}"
                    )
                }
            }
            get("validering") {
                println("kaller Valideringsfeil")
                try {
                    call.respond(skeService.hentValideringsfeil())
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sorry feilet: ${e.message}, \n" +
                                "clientID = ${maskinPortenProperties.clientId} \n " +
                                "wellKnownUrl= ${maskinPortenProperties.authorityEndpoint} \n " +
                                "jwk_kid= ${maskinPortenProperties.rsaKey} \n " +

                                "scopes= ${maskinPortenProperties.scopes} \n +" +
                                "skeurl= ${skeProperties.skeRestUrl} \n " +
                                "Stacktrace= ${e.stackTraceToString()}"
                    )
                }
            }

        }

}