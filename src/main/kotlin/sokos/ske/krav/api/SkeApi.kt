package sokos.ske.krav.api

import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSV
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.service.*
import kotlin.system.exitProcess

fun Routing.skeApi(
    skeService: SkeService,
    statusService: StatusService,
    avstemmingService: AvstemmingService
) {
    val logger = KotlinLogging.logger {}

    route("krav") {

        get("listFiles/{directory}") {
            val files = skeService.testListFiles(call.parameters["directory"].toString())
            call.respond(HttpStatusCode.OK, files)
        }

        get("testftp") {
            val files = skeService.testFtp()
            call.respond(HttpStatusCode.OK, files.map { it.name })
        }


        post("lagFil/{filnavn}") {
            val content: String = call.receiveText()
            val fileName: String = call.parameters["filnavn"].toString()

            val ftp = FtpService()
            ftp.createFile(fileName, Directories.INBOUND, content)
            call.respond(HttpStatusCode.OK)

        }

        get("error") {
            for (i in 0..1000) {
                logger.error("Nå er'e feil igjen, Error: $i")
            }
            call.respond("Nå er det 1000 errors i loggen")
        }

        get("warn") {

            for (i in 0..1000) {
                logger.warn("Nå er'e feil igjen, Warning: $i")
            }
            call.respond("Nå er det 1000 errors i loggen")
        }

        get("shutdown") {
            exitProcess(status = 10)
        }

        get("resend") {
            logger.info("API kall for henting av resending")
            try {
                skeService.resendIkkeReskontroforteKrav()
                call.respond(HttpStatusCode.OK, "kjørt ok")
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Sorry feilet: ${e.message}, \n" +
                            "Stacktrace= ${e.stackTraceToString()}"
                )
            }
        }

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
            println("kaller mottaksstatus")
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
        get("allekrav"){
            val databaseService = DatabaseService(PostgresDataSource())
            call.respond(databaseService.getAllFeilmeldinger().toString())
        }
        get("avstemming"){
            call.respondText(avstemmingService.hentAvstemmingsRapport(), CSV)
        }
        get("avstemming/fil"){
            call.respondText(avstemmingService.hentAvstemminsRapportSomFil(), CSV)
        }

    }
}