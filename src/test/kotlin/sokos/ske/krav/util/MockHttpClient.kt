package sokos.ske.krav.util

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json


object MockHttpClientUtils {
  enum class EndepunktType(val url: String) {
	MOTTAKSSTATUS("/mottaksstatus"),
	OPPRETT("/innkrevingsoppdrag"),
	ENDRE_RENTER("/renter"),
	ENDRE_HOVEDSTOL("/hovedstol"),
	AVSKRIVING("/avskriving"),
	ENDRE_REFERANSE("/oppdragsgiversreferanse"),
  }

  data class MockRequestObj(
	val response: String,
	val urls: List<String>
  )

  fun generateUrls(baseUrl: String) = mutableListOf(
	"/innkrevingsoppdrag/1234$baseUrl",
	"/innkrevingsoppdrag/OB040000592759$baseUrl",
	"/innkrevingsoppdrag/OB040000479803$baseUrl",
	"/innkrevingsoppdrag/OB040000595755$baseUrl",
	"/innkrevingsoppdrag/1111-skeUUID$baseUrl",
	"/innkrevingsoppdrag/2222-skeUUID$baseUrl",
	"/innkrevingsoppdrag/$baseUrl",
	"/innkrevingsoppdrag$baseUrl",
	baseUrl
  )

  object Responses {

	fun mottaksStatusResponse(kravIdentifikator: String = "1234", status: String): String {
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

	fun nyttKravResponse(kravIdentifikator: String) = """{"kravidentifikator": "$kravIdentifikator"}"""
	fun endringResponse(transaksjonsId: String = "791e5955-af86-42fe-b609-d4fc2754e35e") = """{"transaksjonsid": "$transaksjonsId"}"""


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
	fun innkrevingsOppdragHarUgyldigTilstandResponse() =
	  """
        {
        "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-har-ugyldig-tilstand",
        "title":"Innkrevingsoppdrag har ugyldig tilstand",
        "status":409,
        "detail":"Oppdrag med Kravidentifikatortype=SKATTEETATENS_KRAVIDENTIFIKATOR og Kravidentifikator=51dfc1fa-b241-4886-bb8f-a448e80c9b10 er ikke reskontroført og renter kan derfor ikke endres.",
        "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/51dfc1fa-b241-4886-bb8f-a448e80c9b10/renter"
        }
    """.trimIndent()


	//language=json
	fun valideringsfeilResponse(error: String, message: String) =
	  """{
	  "valideringsfeil": [{
		  "error":   "$error",
		  "message": "$message"
			}]
		}
        """.trimMargin()
  }
}


class MockHttpClient() {
  private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
  private val jsonConfig = Json {
	prettyPrint = true
	ignoreUnknownKeys = true
	encodeDefaults = true
	@OptIn(ExperimentalSerializationApi::class)
	explicitNulls = false
  }


  fun getClient(kall: List<MockHttpClientUtils.MockRequestObj>, statusCode: HttpStatusCode) = HttpClient(MockEngine) {
	install(ContentNegotiation) { json(jsonConfig) }
	engine {
	  addHandler { request ->
		val handler = kall.singleOrNull() { it.urls.contains(request.url.encodedPath) }
		if (handler != null) respond(handler.response, statusCode, responseHeaders)
		else error("Ikke implementert: ${request.url.encodedPath}")
	  }
	}
  }
}
