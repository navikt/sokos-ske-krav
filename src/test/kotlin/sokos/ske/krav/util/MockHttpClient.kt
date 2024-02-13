package sokos.ske.krav.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import sokos.ske.krav.database.models.Status

class MockHttpClient(kravident: String = "1234", val iderForValideringsFeil: List<String> = listOf("23", "54", "87")) {
  //language=json
  private val opprettResponse = """{"kravidentifikator": "$kravident"}"""

  //language=json
  private val endringResponse = """{"transaksjonsid":  "5432"}""".trimMargin()

  private fun innkrevingsOppdragEksistererIkkeResponse(kravid: String = "1234"): String {
	//language=json
	return """      
        {
            "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-eksisterer-ikke",
            "title":"Innkrevingsoppdrag eksisterer ikke",
            "status":404,
            "detail":"Innkrevingsoppdrag med oppdragsgiversKravidentifikator=$kravid eksisterer ikke",
            "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/avskriving"
        }
        """.trimIndent()
  }

  //language=json
  private val innkrevingsOppdragHarUgyldigTilstandResponse = """
        {
        "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-har-ugyldig-tilstand",
        "title":"Innkrevingsoppdrag har ugyldig tilstand",
        "status":409,
        "detail":"Oppdrag med Kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR og Kravidentifikator=51dfc1fa-b241-4886-bb8f-a448e80c9b10 er ikke reskontroført og renter kan derfor ikke endres.",
        "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/51dfc1fa-b241-4886-bb8f-a448e80c9b10/renter"
        }
    """.trimIndent()


  private fun mottattResponse(kravid: String = "1234"): String {
	//language=json
	return """
       {
             "kravidentifikator": "$kravid"
            "oppdragsgiversKravidentifikator": "$kravid"
            "mottaksstatus": "${Status.RESKONTROFOERT.value}"
            "statusOppdatert": "2023-10-04T04:47:08.482Z"
            }
        """.trimIndent()
  }


  //language=json
  private val valideringsfeilResponse =
	"""{
        | "valideringsfeil": [{
        |    "error": "feil",
        |    "message": "melding"
            }]
        }
        """.trimMargin()

  private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
  fun getClient(statusCode: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine) {
	install(ContentNegotiation) {
	  json(
		Json {
		  prettyPrint = true
		  ignoreUnknownKeys = true
		  encodeDefaults = true
		  @OptIn(ExperimentalSerializationApi::class)
		  explicitNulls = false
		},
	  )
	}
	engine {
	  addHandler { request ->

		when (request.url.encodedPath) {
		  "/innkrevingsoppdrag/1234/mottaksstatus" -> {
			if (statusCode.isSuccess()) respond(mottattResponse("1234"), statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000479803/mottaksstatus" -> {
			if (statusCode.isSuccess()) respond(mottattResponse("OB040000479803"), statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000479803"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000592759/mottaksstatus" -> {
			if (statusCode.isSuccess()) respond(mottattResponse("OB040000592759"), statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000592759"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000592759/avstemming" -> {
			if (statusCode.isSuccess()) respond(opprettResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000592759"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000479803/avstemming" -> {
			if (statusCode.isSuccess()) respond(opprettResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000479803"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag" -> {
			if (statusCode.isSuccess()) respond(opprettResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragHarUgyldigTilstandResponse, statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/1234/renter" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000592759/renter" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000592759"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000595755/renter" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000595755"), statusCode, responseHeaders)
		  }


		  "/innkrevingsoppdrag/1234/hovedstol" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000592759/hovedstol" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000592759"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000595755/hovedstol" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse("OB040000595755"), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/1234/oppdragsgiversreferanse" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/OB040000592759/oppdragsgiversreferanse" -> {
			if (statusCode.isSuccess()) respond(endringResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/avskriving" -> {
			if (statusCode.isSuccess()) respond("", statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag//mottaksstatus" -> {
			if (statusCode.isSuccess()) respond(mottattResponse("1234"), statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/${iderForValideringsFeil[0]}/valideringsfeil" -> {
			if (statusCode.isSuccess()) respond(valideringsfeilResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(iderForValideringsFeil[0]), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/${iderForValideringsFeil[1]}/valideringsfeil" -> {
			if (statusCode.isSuccess()) respond(valideringsfeilResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(iderForValideringsFeil[1]), statusCode, responseHeaders)
		  }

		  "/innkrevingsoppdrag/${iderForValideringsFeil[2]}/valideringsfeil" -> {
			if (statusCode.isSuccess()) respond(valideringsfeilResponse, statusCode, responseHeaders)
			else respond(innkrevingsOppdragEksistererIkkeResponse(iderForValideringsFeil[2]), statusCode, responseHeaders)
		  }

		  else -> {
			error("Ikke implementert: ${request.url.encodedPath}")
		  }
		}
	  }
	}
  }
}
