package sokos.ske.krav.service


import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource

import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient


internal class SkeServiceTest: FunSpec ({

    test("Test OK filer"){
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val dataSource = mockk<PostgresDataSource>(relaxed = true)
        val ftpService = FtpService()
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt"))
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
        val service = SkeService(client,dataSource, ftpService)
        val responses = service.sendNyeFtpFilerTilSkatt()
        responses.map { it.status shouldBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }
        ftpService.close()
        httpClient.close()
    }

    test("Test feilede filer"){
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val dataSource = mockk<PostgresDataSource>(relaxed = true)
        val ftpService = FtpService()
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt"))
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

        ftpService.close()
        httpClient.close()
    }
})