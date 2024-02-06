package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient

internal class SkeServiceTest : FunSpec({

    test("Test OK filer") {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val mockkKravService = mockk<DatabaseService>(relaxed = true){
            every { getSkeKravident(any<String>()) } returns "1234"
        }
        val fakeFtpService = FakeFtpService()
        val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("AltOkFil.txt"))

        val httpClient = MockHttpClient().getClient()
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val service = SkeService(client, mockkKravService, ftpService)

        val responses = service.sendNewFilesToSKE()
        responses.map { it.status shouldBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

        fakeFtpService.close()
        httpClient.close()
    }

    test("Test feilede filer") {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val mockkKravService = mockk<DatabaseService>(relaxed = true){
            every { getSkeKravident(any<String>()) } returns "1234"
        }
        val fakeFtpService = FakeFtpService()
        val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("AltOkFil.txt"))

        val httpClient = MockHttpClient().getClient(HttpStatusCode.BadRequest)
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val service = SkeService(client, mockkKravService, ftpService)

        val responses = service.sendNewFilesToSKE()
        responses.map { it.status shouldNotBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

        fakeFtpService.close()
        httpClient.close()
    }
})
