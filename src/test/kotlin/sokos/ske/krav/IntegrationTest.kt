package sokos.ske.krav

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.DatabaseTestUtils

@Ignored
internal class IntegrationTest: FunSpec ({


    test("Test insert"){
        val datasource = DatabaseTestUtils.getDataSource("initEmptyDB.sql", false)
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val ftpService = FtpService()
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt"))

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
        val service = SkeService(client, datasource, ftpService)
        service.testRepo()
        ftpService.close()
        httpClient.close()
        datasource.close()
    }
})