package sokos.ske.krav

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.http.*
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer

@Ignored
internal class IntegrationTest : FunSpec({

    val testContainer = TestContainer("IntegrationTest-TestSendNyeKrav")
   val container = testContainer.getContainer(reusable = true, loadFlyway = true)

    val ds = container.toDataSource {
        maximumPoolSize = 8
        minimumIdle = 4
        isAutoCommit = false
    }

    afterSpec {
        ds.close()
        TestContainer().stopAnyRunningContainer()
    }
    fun setupMocks(
        ftpFiler: List<String>,
        clientStatusCode: HttpStatusCode,
        directory: Directories =  Directories.INBOUND,
        kravIdentifikator:String = "1234"): SkeService {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val httpClient = MockHttpClient(kravIdentifikator = kravIdentifikator).getClient(clientStatusCode)
        val ftpService = FakeFtpService().setupMocks(directory, ftpFiler)

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val databaseService = DatabaseService(PostgresDataSource(ds))
        val endreKravService = EndreKravService(skeClient, databaseService)
        val opprettKravService = OpprettKravService(skeClient, databaseService)
        val stoppKravService = StoppKravService(skeClient, databaseService)

        return SkeService(skeClient, stoppKravService, endreKravService, opprettKravService, databaseService, ftpService)
    }
/*
    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {
        val skeService = setupMocks(listOf("AltOkFil.txt"), HttpStatusCode.OK)
        skeService.sendNewFilesToSKE()

        val kravdata = ds.connection.use {
            it.getAllKrav()
        }

        kravdata.size shouldBe 103  // 101 krav + 2 fordi det skal være to endringer og hver endring = 2 rader i DB
    }

    test("Kravdata skal lagres med riktig type"){
        val kravdata = ds.connection.use {
            it.getAllKrav()
        }

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_RENTER }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_HOVEDSTOL }.size shouldBe 2
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
    }

    test("Mottaksstatus skal oppdateres i database") {
        val skeService = setupMocks(listOf("AltOkFil.txt"), HttpStatusCode.OK)
        skeService.hentOgOppdaterMottaksStatus()

        val kravdata = ds.connection.use {
            it.getAllKrav()
        }

        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 103

    }*/

/*    data class ValideringFraDB(
        val kravidentifikatorSKE: String,
        val error: String,
        val melding: String,
        val dato: Timestamp,
    )

    test("Test hent valideringsfeil") {
        val iderForValideringsFeil = listOf("23", "54", "87")
        val httpClient = MockHttpClient(kravident = "1234", iderForValideringsFeil = iderForValideringsFeil).getClient()
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)

        val mockkKravService = mockKravService(ds)
        val skeService = SkeService(skeClient, mockkKravService, mockk<FtpService>())

        ds.connection.use { con ->
            iderForValideringsFeil.forEach { id ->
                con.prepareStatement(
                    """
                update krav
                set 
                kravidentifikator_ske = $id,
                status = '${Status.VALIDERINGSFEIL.value}'
                where id = $id;
                    """.trimIndent(),
                ).executeUpdate()
                con.commit()
            }
        }

        skeService.hentValideringsfeil().size shouldBe 3

        val valideringsFeil = mutableListOf<ValideringFraDB>()
        ds.connection.use { con ->
            val rs: ResultSet = con.prepareStatement(
                """select * from validering where kravidentifikator_ske in('23', '54', '87')""".trimIndent(),
            ).executeQuery()
            while (rs.next()) {
                valideringsFeil.add(
                    ValideringFraDB(
                        rs.getString("kravidentifikator_ske"),
                        rs.getString("error"),
                        rs.getString("melding"),
                        rs.getTimestamp("dato"),
                    ),
                )
            }
        }
        valideringsFeil.size shouldBe 3

        valideringsFeil.forEach {
            it.error shouldBe "feil"
            it.melding shouldBe "melding"
            it.dato.toString() shouldBe "${LocalDate.now()} 00:00:00.0"
        }
        httpClient.close()
    }*/
})

