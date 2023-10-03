package sokos.ske.krav.apis

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.service.SkeService


fun Routing.skeApi(
    skeService: SkeService,
    maskinPortenProperties: PropertiesConfig.MaskinportenClientConfig = PropertiesConfig.MaskinportenClientConfig(),
    skeProperties: PropertiesConfig.SKEConfig = PropertiesConfig.SKEConfig()
) {
    val logger = KotlinLogging.logger {}


    route("krav") {
        get("testresp") {
            skeService.testResponse()
            call.respond(HttpStatusCode.OK)
        }

        get("testFTPSend") {
            logger.info { "kaller ftp send" }
            val responses = skeService.sendNyeFtpFilerTilSkatt()

            val okCodes = responses.filter { it.status.isSuccess() }
            val failedCodes = responses.filter { !it.status.isSuccess() }
            call.respond(HttpStatusCode.OK, "OK: ${okCodes.size}. Feilet: ${failedCodes.size}")
        }


        get("test") {
            logger.info { "API kaller test" }
            try {
                skeService.sendNyeFtpFilerTilSkatt()
                logger.info { "APIKrav sendt, returnerer reponse" }
                call.respond(HttpStatusCode.OK, "Krav sendt")
                logger.info { "APIKrav sendt, oppdaterer mottaksstatus" }
                skeService.hentOgOppdaterMottaksStatus()
                logger.info { "APIKrav sendt, har oppdatert mottaksstatus" }
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
            logger.info { "APIlogger:  Status Start" }
            try {
                call.respond(skeService.hentOgOppdaterMottaksStatus())
                logger.info { "APILogger Status ferdig" }
            } catch (e: Exception) {
                logger.error { " APILogger: Status feilet" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "APISorry feilet: ${e.message}, \n" +
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
            logger.info("API kaller Valideringsfeil")
            try {
                val a = skeService.hentValideringsfeil()
                if (a.isEmpty()) call.respond("ingen valideringsfeil funnet")
                else call.respond(a)
            } catch (e: Exception) {
                logger.error { "APIlogger: validering feilet" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Sorry validering feilet: ${e.message}, \n" +
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