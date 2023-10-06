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
import sokos.ske.krav.service.FakeFtpService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.DatabaseTestUtils


val opprettResponse = "{\"kravidentifikator\": \"1234\"}"
val mottattresponse = "{\n" +
        "  \"kravidentifikator\": \"1234\",\n" +
        "  \"oppdragsgiversKravidentifikator\": \"1234\",\n" +
        "  \"mottaksstatus\": \"MOTTATT_UNDER_BEHANDLING\",\n" +
        "  \"statusOppdatert\": \"2023-10-04T04:47:08.482Z\"\n" +
        "}"

@Ignored
internal class IntegrationTest: FunSpec ({

    test("Test insert"){
        val datasource = DatabaseTestUtils.getDataSource("initEmptyDB.sql", false)
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val fakeFtpService = FakeFtpService()
        fakeFtpService.connect(Directories.OUTBOUND, listOf("fil1.txt"))

        val mockEngineOK = MockEngine {
            respond(
                content = ByteReadChannel("{\"kravidentifikator\": \"1234\"}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClientSendKrav = HttpClient(mockEngineOK) {
            expectSuccess = false
        }
        val clientSendKrav = SkeClient(skeEndpoint = "", client = httpClientSendKrav, tokenProvider = tokenProvider)
     //   val serviceSendKrav = SkeService(clientSendKrav, datasource, fakeFtpService)
     val serviceSendKrav = SkeService(clientSendKrav, datasource)

        serviceSendKrav.sendNyeFtpFilerTilSkatt(15)

        httpClientSendKrav.close()
        val content = ByteReadChannel("{\n" +
                "  \"kravidentifikator\": \"1234\",\n" +
                "  \"oppdragsgiversKravidentifikator\": \"1234\",\n" +
                "  \"mottaksstatus\": \"MOTTATT_UNDER_BEHANDLING\",\n" +
                "  \"statusOppdatert\": \"2023-10-04T04:47:08.482Z\"\n" +
                "}")

        val config = MockEngineConfig()
        config.addHandler {
            respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        ) }
        val eengine = MockEngine(config)
        val mockEngineMottaksstatus= MockEngine {
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClientMottaksstatus = HttpClient(mockEngineMottaksstatus) {
            expectSuccess = false
        }


        val clientMottaksstatus= SkeClient(skeEndpoint = "", client = httpClientMottaksstatus, tokenProvider = tokenProvider)
       // val serviceMottaksstatus = SkeService(clientMottaksstatus, datasource, fakeFtpService)
        val serviceMottaksstatus = SkeService(clientMottaksstatus, datasource)

        val kravdata = serviceMottaksstatus.hentOgOppdaterMottaksStatus()

        println(kravdata)
        httpClientMottaksstatus.close()
        fakeFtpService.close()

        datasource.close()
    }

    test("foo"){
        val datasource = DatabaseTestUtils.getDataSource("initEmptyDB.sql", false)
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val fakeFtpService = FakeFtpService()
        fakeFtpService.connect(Directories.OUTBOUND, listOf("fil1.txt"))

        val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/innkrevingsoppdrag/1234/mottaksstatus" -> {
                            respond(mottattresponse, HttpStatusCode.OK, responseHeaders)
                        }
                        "/innkrevingsoppdrag" -> {
                            respond(opprettResponse, HttpStatusCode.OK, responseHeaders)
                        }
                        else -> {
                            error("Unhandled ${request.url.encodedPath}")
                        }
                    }
                }
            }
        }

        val clientSendKrav = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
       val service = SkeService(clientSendKrav, datasource)
       // val service = SkeService(clientSendKrav, datasource, fakeFtpService)

        service.sendNyeFtpFilerTilSkatt(15)
        val kravdata =  service.hentOgOppdaterMottaksStatus()
        println(kravdata)

        client.close()

        fakeFtpService.close()
        datasource.close()

    }
})