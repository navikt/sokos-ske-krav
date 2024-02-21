package sokos.ske.krav.service

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.mockk
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllKrav
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer


internal class SkeServiceTest : FunSpec({


    fun startContainer(containerName: String): HikariDataSource {
        return TestContainer(containerName)
            .getContainer(reusable = false, loadFlyway = true)
            .toDataSource {
                maximumPoolSize = 8
                minimumIdle = 4
                isAutoCommit = false
            }


    }

    fun setupMocks(
        ftpFiler: List<String>,
        clientStatusCode: HttpStatusCode,
        directory: Directories = Directories.INBOUND,
        dataSource: HikariDataSource,
        kravIdentifikator: String = "1234"
    ): SkeService {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val httpClient = MockHttpClient(kravIdentifikator = kravIdentifikator).getClient(clientStatusCode)
        val ftpService = FakeFtpService().setupMocks(directory, ftpFiler)

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val databaseService = DatabaseService(PostgresDataSource(dataSource))
        val endreKravService = EndreKravService(skeClient, databaseService)
        val opprettKravService = OpprettKravService(skeClient, databaseService)
        val stoppKravService = StoppKravService(skeClient, databaseService)

        return SkeService(
            skeClient,
            stoppKravService,
            endreKravService,
            opprettKravService,
            databaseService,
            ftpService
        )
    }

    fun setupMocks(
        ftpFiler: List<String>,
        clientStatusCode: HttpStatusCode,
        containerName: String,
        directory: Directories = Directories.INBOUND,
        kravIdentifikator: String = "1234"
    ): Pair<SkeService, HikariDataSource> {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val httpClient = MockHttpClient(kravIdentifikator = kravIdentifikator).getClient(clientStatusCode)
        val ftpService = FakeFtpService().setupMocks(directory, ftpFiler)

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val dataSource = startContainer(containerName)
        val databaseService = DatabaseService(PostgresDataSource(dataSource))
        val endreKravService = EndreKravService(skeClient, databaseService)
        val opprettKravService = OpprettKravService(skeClient, databaseService)
        val stoppKravService = StoppKravService(skeClient, databaseService)

        return Pair(SkeService(skeClient, stoppKravService, endreKravService, opprettKravService, databaseService, ftpService), dataSource)
    }


    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("FilMedBare10Linjer.txt"), HttpStatusCode.OK, this.testCase.name.testName)
        mocks.first.sendNewFilesToSKE()

        val rs = mocks.second.connection.prepareStatement(
            """
		select count(*) as a from krav
	  """.trimIndent()
        )
            .executeQuery()
        rs.next()
        val kravdata = rs.getInt("a")


        kravdata shouldBe 10 

    }
    test("Kravdata skal lagres med type som beskriver hva slags krav det er") {
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("AltOkFil.txt"), HttpStatusCode.OK, this.testCase.name.testName)
        mocks.first.sendNewFilesToSKE()

        val kravdata = mocks.second.connection.use {
            it.getAllKrav()
        }

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_RENTER }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_HOVEDSTOL }.size shouldBe 2
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97

    }


    test("Mottaksstatus skal oppdateres i database") {
        val dataSource = startContainer(this.testCase.name.testName)
        val skeService = setupMocks(listOf("AltOkFil.txt"), HttpStatusCode.OK, dataSource = dataSource)
        skeService.sendNewFilesToSKE()
        skeService.hentOgOppdaterMottaksStatus()

        val kravdata = dataSource.connection.use {
            it.getAllKrav()
        }

        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 103
    }

    test("Når et krav feiler skal det lagres i feilmeldingtabell") {
        val dataSource = startContainer(this.testCase.name.testName)
        val skeService =
            setupMocks(ftpFiler = listOf("FilMedOrgNr.txt"), HttpStatusCode.BadRequest, dataSource = dataSource)
        skeService.sendNewFilesToSKE()

        val feilmeldinger =
            dataSource.connection.prepareStatement("SELECT * FROM FEILMELDING").executeQuery().toFeilmelding()

        feilmeldinger.size shouldBe 8
        feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 409 }.size shouldBe 8
    }

    /*

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
        }*/
})
