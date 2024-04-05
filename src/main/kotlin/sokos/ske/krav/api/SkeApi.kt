package sokos.ske.krav.api

import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSV
import io.ktor.http.ContentType.Text.Html
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService

fun Routing.skeApi(
    skeService: SkeService,
    statusService: StatusService,
    avstemmingService: AvstemmingService
) {
    val logger = KotlinLogging.logger {}

    route("krav") {

        get("test") {
            logger.info("API kaller test")
            try {
                //val response = skeService.sendNewFilesToSKE()
                val response = skeService.handleNewKrav()
                logger.info("APIKrav sendt, returnerer reponse")
                call.respond(HttpStatusCode.OK, "$response")
                logger.info("APIKrav sendt")
                /*                logger.info { "APIKrav sendt, oppdaterer mottaksstatus" }
                                skeService.hentOgOppdaterMottaksStatus()
                                logger.info { "APIKrav sendt, har oppdatert mottaksstatus" }*/
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Sorry feilet: ${e.message}, \n" +
                            "Stacktrace= ${e.stackTraceToString()}"
                )
            }
        }
        get("status") {
            logger.info("APIlogger:  Status Start")
            try {
                call.respond(statusService.hentOgOppdaterMottaksStatus())
                logger.info("APILogger Status ferdig")
            } catch (e: Exception) {
                logger.error("APILogger: Status feilet")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "APISorry feilet: ${e.message}, \n" +
                            "Stacktrace= ${e.stackTraceToString()}"
                )
            }
        }
        get("validering") {
            logger.info("API kaller Valideringsfeil")
            try {
                statusService.hentValideringsfeil()
            } catch (e: Exception) {
                logger.error("APIlogger: validering feilet")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Sorry validering feilet: ${e.message}, \n" +
                            "Stacktrace= ${e.stackTraceToString()}"
                )
            }
        }
        get("allekrav") {
            val databaseService = DatabaseService(PostgresDataSource())
            call.respond(databaseService.getAllErrorMessages().toString())
        }
        get("avstemming") {
            call.respondText(avstemmingService.hentAvstemmingsRapport(), Html)
        }
        get("avstemming/fil") {
            call.respondText(avstemmingService.hentAvstemminsRapportSomFil(), CSV)
        }
        get("avstemming/update/{kravid}") {
            val id = call.parameters["kravid"]
            if (!id.isNullOrBlank()) avstemmingService.oppdaterAvstemtKravTilRapportert(id.toInt())
            call.respondRedirect("/krav/avstemming", permanent = true)
        }
        get("avstemming/feilfiler") {
            call.respondText(avstemmingService.visFeilFiler(), Html)
        }
        get("resending") {
            call.respondText(avstemmingService.hentKravSomSkalresendes(), Html)
        }

    }
}