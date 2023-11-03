package sokos.ske.krav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.SkeService


fun Routing.skeApi(
	skeService: SkeService,
) {
	val logger = KotlinLogging.logger {}

	route("krav") {

		get("listFiles/{directory}") {
			val files = skeService.testListFiles(call.parameters["directory"].toString() + "/test")
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
			(0..1000).forEach{
				logger.error { "Nå er'e feil igjen $it av 1000" }
			}
		}



		get("test") {
			logger.info { "API kaller test" }
			try {
				val response = skeService.sendNyeFtpFilerTilSkatt()
				logger.info { "APIKrav sendt, returnerer reponse" }
				call.respond(HttpStatusCode.OK, "$response")
				logger.info { "APIKrav sendt, oppdaterer mottaksstatus" }
				skeService.hentOgOppdaterMottaksStatus()
				logger.info { "APIKrav sendt, har oppdatert mottaksstatus" }
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
			logger.info { "APIlogger:  Status Start" }
			try {
				call.respond(skeService.hentOgOppdaterMottaksStatus())
				logger.info { "APILogger Status ferdig" }
			} catch (e: Exception) {
				logger.error { " APILogger: Status feilet" }
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
				val a = skeService.hentValideringsfeil()
				if (a.isEmpty()) call.respond("ingen valideringsfeil funnet")
				else call.respond(a)
			} catch (e: Exception) {
				logger.error { "APIlogger: validering feilet" }
				call.respond(
					HttpStatusCode.InternalServerError,
					"Sorry validering feilet: ${e.message}, \n" +
							"Stacktrace= ${e.stackTraceToString()}"
				)
			}
		}
	}
}