package sokos.ske.krav.api

import io.ktor.http.ContentType.Text.CSV
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import mu.KotlinLogging
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService

fun Routing.skeApi(
    skeService: SkeService,
    statusService: StatusService,
    avstemmingService: AvstemmingService,
    ftpService: FtpService,
) {
    val logger = KotlinLogging.logger("secureLogger")

    route("krav") {
        get("test") {
            logger.info("API : Kaller handleNewKrav")
            try {
                call.respond(HttpStatusCode.OK, "Da er den i gang ${Clock.System.now()}")
                skeService.handleNewKrav()
                logger.info("API :  Krav sendt")
            } catch (e: Exception) {
                logger.error(
                    "API : Sorry feilet: ${e.message}, \n" +
                        "Stacktrace= ${e.stackTraceToString()}",
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
                        "Stacktrace= ${e.stackTraceToString()}",
                )
            }
        }
        get("alleFeilmeldinger") {
            logger.info("API kaller Valideringsfeil")
            try {
                statusService.hentValideringsfeil()
            } catch (e: Exception) {
                logger.error("APIlogger: validering feilet")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Sorry validering feilet: ${e.message}, \n" +
                        "Stacktrace= ${e.stackTraceToString()}",
                )
            }
        }
        get("avstemming") {
            call.respondText(avstemmingService.hentAvstemmingsRapport(), Html)
        }
        get("avstemming/fil") {
            call.respondText(avstemmingService.hentAvstemmingsRapportSomCSVFil(), CSV)
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
        // hjelpe saker under utvikling
        get("filer") {
            val liste = mutableListOf<String>()
            liste.add("INNBOUND")
            liste.addAll(ftpService.listFiles(Directories.INBOUND))
            liste.add("OUTBOUND")
            liste.addAll(ftpService.listFiles(Directories.OUTBOUND))
            liste.add("FEIL-FILER")
            liste.addAll(ftpService.listFiles(Directories.FAILED))
            call.respond(liste.joinToString("\n"))
        }
    }
}
