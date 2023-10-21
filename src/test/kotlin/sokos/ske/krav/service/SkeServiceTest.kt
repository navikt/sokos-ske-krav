package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromJsonElement
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import sokos.ske.krav.api.model.responses.ValideringsFeilResponse
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient

internal class SkeServiceTest : FunSpec({

	test("validering deserialisering") {
		val json =
			Json.parseToJsonElement("""{"valideringsfeil":[{"error":"PERSON_ER_DOED","message":"Person med fødselsdato=318830 er død"}]}""")
		val str =
			"""{"valideringsfeil":[{"error":"PERSON_ER_DOED","message":"Person med fødselsdato=318830 er død"}]}"""
		val valideringsFeil1 = decodeFromJsonElement(ValideringsFeilResponse.serializer(), json)
		val valideringsFeil2 = decodeFromString<ValideringsFeilResponse>(str)

		val res1 = encodeToString(ValideringsFeilResponse.serializer(), valideringsFeil1)

		println(valideringsFeil1.valideringsfeil.map { " Feil1: ${it.error}, ${it.message}" })
		println(valideringsFeil2.valideringsfeil.map { " Feil1: ${it.error}, ${it.message}" })

		println("And back again: $res1")
	}

	test("Test OK filer") {
		val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
		val dataSource = mockk<PostgresDataSource>(relaxed = true)
		val fakeFtpService = FakeFtpService()
		val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt"))

		val httpClient = MockHttpClient().getClient()
		val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
		val service = SkeService(client, dataSource, ftpService)

		val responses = service.sendNyeFtpFilerTilSkatt()
		responses.map { it.status shouldBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

		fakeFtpService.close()
		httpClient.close()
	}

	test("Test feilede filer") {
		val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
		val dataSource = mockk<PostgresDataSource>(relaxed = true)
		val fakeFtpService = FakeFtpService()
		val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt"))

		val httpClient = MockHttpClient().getClient(HttpStatusCode.BadRequest)
		val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
		val service = SkeService(client, dataSource, ftpService)

		val responses = service.sendNyeFtpFilerTilSkatt()
		responses.map { it.status shouldNotBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

		fakeFtpService.close()
		httpClient.close()
	}
})