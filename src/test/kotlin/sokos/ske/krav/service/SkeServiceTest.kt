package sokos.ske.krav.service


import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromJsonElement
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import sokos.ske.krav.FakeFtpService
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.skemodels.responses.ValideringsfeilResponse


internal class SkeServiceTest: FunSpec ({

    test("validering deserialisering"){
        val json = Json.parseToJsonElement("""{"valideringsfeil":[{"error":"PERSON_ER_DOED","message":"Person med fødselsdato=318830 er død"}]}""")
        val str = """{"valideringsfeil":[{"error":"PERSON_ER_DOED","message":"Person med fødselsdato=318830 er død"}]}"""
        val valideringsFeil1 = decodeFromJsonElement(ValideringsfeilResponse.serializer(), json)
        val valideringsFeil2 = decodeFromString<ValideringsfeilResponse>(str)

        val res1 = encodeToString(ValideringsfeilResponse.serializer(), valideringsFeil1)

        println(valideringsFeil1.valideringsfeil.map { " Feil1: ${it.error}, ${it.message}"} )
        println(valideringsFeil2.valideringsfeil.map { " Feil1: ${it.error}, ${it.message}"} )

        println("And back again: $res1")

    }


    test("Test OK filer"){
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val dataSource = mockk<PostgresDataSource>(relaxed = true)
        val fakeFtpService = FakeFtpService()

        val ftpService =  fakeFtpService.setupMocks(Directories.OUTBOUND, listOf("fil1.txt"))
        val mockEngineOK = MockEngine {
            respond(
                content = ByteReadChannel("{\"kravidentifikator\": \"1234\"}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngineOK) {
            expectSuccess = false
        }
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
      //  val service = SkeService(client,dataSource, fakeFtpService)
        val service = SkeService(client,dataSource, ftpService)
        val responses = service.sendNyeFtpFilerTilSkatt()
        responses.map { it.status shouldBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }
        fakeFtpService.close()
        httpClient.close()
    }

    test("Test feilede filer"){
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val dataSource = mockk<PostgresDataSource>(relaxed = true)
        val fakeFtpService = FakeFtpService()
        val ftpService = fakeFtpService.setupMocks(Directories.OUTBOUND, listOf("fil1.txt"))
        val mockEngineFail = MockEngine {
            respond(
                content = ByteReadChannel("Feil i request"),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngineFail) {
            expectSuccess = false
        }
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val service = SkeService(client,dataSource, ftpService)

        val responses = service.sendNyeFtpFilerTilSkatt()
        responses.map { it.status shouldNotBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

        fakeFtpService.close()
        httpClient.close()
    }
})