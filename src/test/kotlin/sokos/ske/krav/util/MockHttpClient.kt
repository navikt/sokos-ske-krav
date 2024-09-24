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

object MockHttpClientUtils {
    enum class EndepunktType(
        val url: String,
    ) {
        MOTTAKSSTATUS("/mottaksstatus"),
        OPPRETT("/innkrevingsoppdrag"),
        ENDRE_RENTER("/renter"),
        ENDRE_HOVEDSTOL("/hovedstol"),
        AVSKRIVING("/avskriving"),
        ENDRE_REFERANSE("/oppdragsgiversreferanse"),
        HENT_VALIDERINGSFEIL("/valideringsfeil"),
    }

    data class MockRequestObj(
        val response: String,
        val type: EndepunktType,
        val statusCode: HttpStatusCode,
    )

    object Responses {
        fun mottaksStatusResponse(
            kravIdentifikator: String = "1234",
            status: String = "RESKONTROFOERT",
        ): String {
            //language=json
            return """
                {
                     "kravidentifikator": "$kravIdentifikator"
                     "oppdragsgiversKravidentifikator": "4321"
                     "mottaksstatus": "$status"
                     "statusOppdatert": "2023-10-04T04:47:08.482Z"
                     }
                """.trimIndent()
        }

        fun nyttKravResponse(kravIdentifikator: String = "1234") = """{"kravidentifikator": "$kravIdentifikator"}"""

        fun nyEndringResponse(transaksjonsId: String = "791e5955-af86-42fe-b609-d4fc2754e35e") = """{"transaksjonsid": "$transaksjonsId"}"""

        fun innkrevingsOppdragEksistererIkkeResponse(kravIdentifikator: String = "1234") =
            //language=json
            """      
            {
                "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-eksisterer-ikke",
                "title":"Innkrevingsoppdrag eksisterer ikke",
                "status":404,
                "detail":"Innkrevingsoppdrag med oppdragsgiversKravidentifikator=$kravIdentifikator eksisterer ikke",
                "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/avskriving"
            }
            """.trimIndent()

        //language=json
        fun valideringsfeilResponse(
            error: String,
            message: String,
        ) = """
            {
            "valideringsfeil": [{
              "error":   "$error",
              "message": "$message"
            	}]
            }
            """.trimMargin()

        //language=json
        fun emptyValideringsfeilResponse() =
            """
            {
            "valideringsfeil": []
            }
            """.trimMargin()
    }
}

class MockHttpClient {
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    private val jsonConfig =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            @OptIn(ExperimentalSerializationApi::class)
            explicitNulls = false
        }

    fun getClient(kall: List<MockHttpClientUtils.MockRequestObj>) =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(jsonConfig) }
            engine {
                addHandler { request ->
                    val handler =
                        kall.singleOrNull {
                            generateUrls(it.type.url).contains(request.url.encodedPath)
                        }
                    if (handler != null) {
                        respond(handler.response, handler.statusCode, responseHeaders)
                    } else {
                        error("Ikke implementert: ${request.url.encodedPath}")
                    }
                }
            }
        }

    private fun generateUrls(baseUrl: String) =
        listOf(
            "/innkrevingsoppdrag/1234$baseUrl",
            "/innkrevingsoppdrag/OB040000592759$baseUrl",
            "/innkrevingsoppdrag/OB040000479803$baseUrl",
            "/innkrevingsoppdrag/OB040000595755$baseUrl",
            "/innkrevingsoppdrag/2220-navsaksnummer$baseUrl",
            "/innkrevingsoppdrag/1111-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1112-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1113-skeUUID$baseUrl",
            "/innkrevingsoppdrag/2222-skeUUID$baseUrl",
            "/innkrevingsoppdrag/3333-skeUUID$baseUrl",
            "/innkrevingsoppdrag/4444-skeUUID$baseUrl",
            "/innkrevingsoppdrag/5555-skeUUID$baseUrl",
            "/innkrevingsoppdrag/6666-skeUUID$baseUrl",
            "/innkrevingsoppdrag/7777-skeUUID$baseUrl",
            "/innkrevingsoppdrag/8888-skeUUID$baseUrl",
            "/innkrevingsoppdrag/9999-skeUUID$baseUrl",
            "/innkrevingsoppdrag/1010-skeUUID$baseUrl",
            "/innkrevingsoppdrag/$baseUrl",
            "/innkrevingsoppdrag$baseUrl",
            baseUrl,
        )
}
