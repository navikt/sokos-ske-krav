package sokos.ske.krav.util

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse

class MockHttpClient(kravident: String = "1234", val iderForValideringsFeil: List<String> = listOf("23", "54", "87")) {
	private val opprettResponse = "{\"kravidentifikator\": \"$kravident\"}"

	private val mottattResponse = "{\n" +
			"  \"kravidentifikator\": \"$kravident\",\n" +
			"  \"oppdragsgiversKravidentifikator\": \"$kravident\",\n" +
			"  \"mottaksstatus\": \"${MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value}\",\n" +
			"  \"statusOppdatert\": \"2023-10-04T04:47:08.482Z\"\n" +
			"}"

	private val valideringsfeilResponse = "{\n" +
			"  \"valideringsfeil\": [\n" +
			"    {\n" +
			"      \"error\": \"feil\",\n" +
			"      \"message\": \"melding\"\n" +
			"    }\n" +
			"  ]\n" +
			"}"

	private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
	fun getClient(statusCode: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine) {
		engine {
			addHandler { request ->
				when (request.url.encodedPath) {
					"/innkrevingsoppdrag/1234/mottaksstatus" -> {
						respond(mottattResponse, statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag//mottaksstatus" -> { //fordi stopp ikke er implementert
						respond(mottattResponse, statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag" -> {
						respond(opprettResponse, statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag/avskriving" -> {
						respond("", statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag/${iderForValideringsFeil[0]}/valideringsfeil" -> {
						respond(valideringsfeilResponse, statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag/${iderForValideringsFeil[1]}/valideringsfeil" -> {
						respond(valideringsfeilResponse, statusCode, responseHeaders)
					}

					"/innkrevingsoppdrag/${iderForValideringsFeil[2]}/valideringsfeil" -> {
						respond(valideringsfeilResponse, statusCode, responseHeaders)
					}

					else -> {
						error("Ikke implementert: ${request.url.encodedPath}")
					}
				}
			}
		}
	}
}