package sokos.ske.krav.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse

class MockHttpClient(kravident: String = "1234", val iderForValideringsFeil: List<String> = listOf("23", "54", "87")) {
    //language=json
    private val opprettResponse = """{"kravidentifikator": "$kravident"}"""

    //language=json
    private val mottattResponse =
        """{
        | "kravidentifikator": "$kravident"
        |"oppdragsgiversKravidentifikator": "$kravident"
        |"mottaksstatus": "${MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value}"
        |"statusOppdatert": "2023-10-04T04:47:08.482Z"
        }
        """.trimMargin()

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
                        respond(mottattResponse, statusCode, responseHeaders)
                    }
                    "/innkrevingsoppdrag//mottaksstatus" -> { // hva f gjør vi nå
                        respond(mottattResponse, statusCode, responseHeaders)
                    }

                    "/innkrevingsoppdrag" -> {
                        respond(opprettResponse, statusCode, responseHeaders)
                    }

                    "/innkrevingsoppdrag/1234/renter" -> {
                        respond("", statusCode, responseHeaders)
                    }

                    "/innkrevingsoppdrag//renter" -> { // hva f fjør vi nå
                        respond("", statusCode, responseHeaders)
                    }

                    "/innkrevingsoppdrag//hovedstol" -> { // hva f fjør vi nå
                        respond("", statusCode, responseHeaders)
                    }
                    "/innkrevingsoppdrag/1234/hovedstol" -> {
                        respond("", statusCode, responseHeaders)
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
