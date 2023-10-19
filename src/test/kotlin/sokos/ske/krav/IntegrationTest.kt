package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.Repository.hentAlleKravData
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse
import sokos.ske.krav.util.TestContainer
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate


val kravident = "1234"
val opprettResponse = "{\"kravidentifikator\": \"$kravident\"}"

val mottattresponse = "{\n" +
        "  \"kravidentifikator\": \"$kravident\",\n" +
        "  \"oppdragsgiversKravidentifikator\": \"$kravident\",\n" +
        "  \"mottaksstatus\": \"${ MottaksstatusResponse.Mottaksstatus.RESKONTROFOERT.value}\",\n" +
        "  \"statusOppdatert\": \"2023-10-04T04:47:08.482Z\"\n" +
        "}"

val valideringsfeilResponse = "{\n" +
        "  \"valideringsfeil\": [\n" +
        "    {\n" +
        "      \"error\": \"feil\",\n" +
        "      \"message\": \"melding\"\n" +
        "    }\n" +
        "  ]\n" +
        "}"

val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
val iderForValideringsFeil = listOf("23", "54", "87")


internal class IntegrationTest: FunSpec ({
    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

    afterSpec{
        TestContainer().stopAnyRunningContainer()
    }

    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE"){
        val testContainer = TestContainer("IntegrationTest-TestSendNyeKrav")
        val datasource = testContainer.getDataSource(reusable = true, loadFlyway = true)
        val client = getClient()

        val fakeFtpService = FakeFtpService()
        val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt"))

        val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
        val service = SkeService(mockClient, datasource, ftpService)

        service.sendNyeFtpFilerTilSkatt()

        val kravdata = datasource.connection.hentAlleKravData()
        kravdata.size shouldBe 101

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_KRAV }.size shouldBe 0
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 99
        kravdata.filter { it.kravtype == NYTT_KRAV && it.saksnummer_ske==kravident}.size shouldBe 99

        client.close()
        fakeFtpService.close()
        datasource.close()

    }


    test("Mottaksstatus skal oppdateres i database"){
        val client = getClient()
        val datasource = TestContainer().getRunningContainer("IntegrationTest-TestSendNyeKrav")
        val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
        val service = SkeService(mockClient, datasource, mockk<FtpService>())

        service.hentOgOppdaterMottaksStatus()

        val kravdata = datasource.connection.hentAlleKravData()


        kravdata.filter { it.status ==  MottaksstatusResponse.Mottaksstatus.RESKONTROFOERT.value}.size shouldBe 99

        client.close()
        datasource.close()
    }

    data class ValideringFraDB( val saksnummer_ske: String, val error: String, val melding: String, val dato: Timestamp)

    test("Test hent valideringsfeil"){
        val client = getClient()
        val datasource = TestContainer().getRunningContainer("IntegrationTest-TestSendNyeKrav")
        val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
        val service = SkeService(mockClient, datasource, mockk<FtpService>())


        datasource.connection.use{ con ->
            iderForValideringsFeil.forEach { id ->
                con.prepareStatement("""
                update krav
                set 
                saksnummer_ske = $id,
                status = '${MottaksstatusResponse.Mottaksstatus.VALIDERINGSFEIL.value}'
                where krav_id = $id;
            """.trimIndent()).executeUpdate()
                con.commit()
            }
        }



        service.hentValideringsfeil().size shouldBe 3
        val valideringsFeil = mutableListOf<ValideringFraDB>()
        datasource.connection.use { con ->
            val rs: ResultSet =  con.prepareStatement(
                """select * from validering where saksnummer_ske in('23', '54', '87')""".trimIndent()
            ).executeQuery()
            while(rs.next()){
                valideringsFeil.add(ValideringFraDB(
                    rs.getString("saksnummer_ske"),
                    rs.getString("error"),
                    rs.getString("melding"),
                    rs.getTimestamp("dato")))
            }
        }
        valideringsFeil.size shouldBe 3

        valideringsFeil.forEach {
            it.error shouldBe "feil"
            it.melding shouldBe "melding"
            it.dato.toString() shouldBe "${LocalDate.now()} 00:00:00.0"
        }

    }
})




fun getClient() = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/innkrevingsoppdrag/1234/mottaksstatus"-> {
                    respond(mottattresponse, HttpStatusCode.OK, responseHeaders)
                }
                "/innkrevingsoppdrag//mottaksstatus"-> { //fordi stopp ikke er implementert
                    respond(mottattresponse, HttpStatusCode.OK, responseHeaders)
                }
                "/innkrevingsoppdrag" -> {
                    respond(opprettResponse, HttpStatusCode.OK, responseHeaders)
                }
                "/innkrevingsoppdrag/avskriving" -> {
                    respond("", HttpStatusCode.OK, responseHeaders)
                } "/innkrevingsoppdrag/${iderForValideringsFeil[0]}/valideringsfeil" -> {
                    respond(valideringsfeilResponse, HttpStatusCode.OK, responseHeaders)
                }"/innkrevingsoppdrag/${iderForValideringsFeil[1]}/valideringsfeil" -> {
                    respond(valideringsfeilResponse, HttpStatusCode.OK, responseHeaders)
                }"/innkrevingsoppdrag/${iderForValideringsFeil[2]}/valideringsfeil" -> {
                    respond(valideringsfeilResponse, HttpStatusCode.OK, responseHeaders)
                }

                else -> {
                    error("Ikke implementert: ${request.url.encodedPath}")
                }
            }
        }
    }
}
