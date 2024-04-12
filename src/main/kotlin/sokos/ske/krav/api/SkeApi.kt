package sokos.ske.krav.api

import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSV
import io.ktor.http.ContentType.Text.Html
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
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
    val logger = KotlinLogging.logger ("secureLogger")

    route("krav") {

        get("test") {
            logger.info("API : Kaller handleNewKrav")
            try {
                call.respond(HttpStatusCode.OK, "Da er den i gang ${Clock.System.now()}")
                skeService.handleNewKrav()
                logger.info("API :  Krav sendt")
            } catch (e: Exception) {
                logger.error("API : Sorry feilet: ${e.message}, \n" +
                            "Stacktrace= ${e.stackTraceToString()}")
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