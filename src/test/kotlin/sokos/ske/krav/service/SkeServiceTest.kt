package sokos.ske.krav.service


import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient

import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient


internal class SkeServiceTest: FunSpec ({

    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

    test("Test OK filer"){
        val mockEngineOK = MockEngine {
            respond(
                content = ByteReadChannel("{\"kravidentifikator\": \"1234\"}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngineOK) {
            expectSuccess = false
            install(Logging){ level = LogLevel.INFO}
        }
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val service = SkeService(client, FtpService().apply {connect(fileNames = listOf("fil1.txt")) })
        val responses = service.sendNyeFtpFilerTilSkatt()
        responses.map { it.status shouldBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }
        httpClient.close()
    }

    test("Test feilede filer"){
        val mockEngineFail = MockEngine {
            respond(
                content = ByteReadChannel("Feil i request"),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngineFail) {
            expectSuccess = false
            install(Logging){ level = LogLevel.INFO}
        }
        val client = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val service = SkeService(client, FtpService().apply {connect(fileNames = listOf("fil1.txt")) })
        val responses = service.sendNyeFtpFilerTilSkatt()
        responses.map { it.status shouldNotBeIn listOf(HttpStatusCode.OK, HttpStatusCode.Created) }

        httpClient.close()
    }
})