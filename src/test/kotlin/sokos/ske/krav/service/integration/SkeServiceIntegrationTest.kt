package sokos.ske.krav.service.integration

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClientUtils.EndepunktType
import sokos.ske.krav.util.MockHttpClientUtils.MockRequestObj
import sokos.ske.krav.util.MockHttpClientUtils.Responses
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.setUpMockHttpClient
import sokos.ske.krav.util.setupMocksWithMockEngine
import sokos.ske.krav.util.setupSkeServiceMock
import sokos.ske.krav.util.setupSkeServiceMockWithMockEngine
import sokos.ske.krav.util.startContainer

internal class SkeServiceIntegrationTest : FunSpec({


    test("Når SkeService leser inn en fil skal kravene lagres i database") {
        val dataSource = startContainer(this.testCase.name.testName, emptyList())
        val ftpService = FakeFtpService().setupMocks(Directories.INBOUND, listOf("10NyeKrav.txt"))

        val dsMock = mockk<DatabaseService> {
            every { getAllUnsentKrav() } returns emptyList()
            every { saveAllNewKrav(any<List<KravLinje>>()) } answers { dataSource.connection.use { it.insertAllNewKrav(arg(0)) } }
            every { getSkeKravidentifikator(any<String>()) } answers { dataSource.connection.use { it.getSkeKravidentifikator(arg(0)) } }
            every { updateEndringWithSkeKravIdentifikator(any<String>(), any<String>()) } answers { dataSource.connection.use { it.updateEndringWithSkeKravIdentifikator(arg(0), arg(1)) } }
        }

        val skeService = setupSkeServiceMock(databaseService = dsMock, ftpService = ftpService)
        val skeMock = spyk(skeService, recordPrivateCalls = true)

        justRun { skeMock["resendKrav"]() }

        skeMock.handleNewKrav()

        val kravEtter = dataSource.connection.getAllKrav()
        kravEtter.size shouldBe 10
    }

    test("Etter at kravene lagres i database skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra database") {
        val dataSource = startContainer(this.testCase.name.testName, listOf("10NyeKrav.sql"))
        val ftpService = FakeFtpService().setupMocks(Directories.INBOUND, listOf("TestEndringKravident.txt"))

        val skeClient = mockk<SkeClient> {
            coEvery { getSkeKravidentifikator("8888-navsaksnr") } returns mockk<HttpResponse>() {
                coEvery { body<OpprettInnkrevingsOppdragResponse>().kravidentifikator } returns "8888-skeUUID"
            }

            coEvery { getSkeKravidentifikator("2222-navsaksnr") } returns mockk<HttpResponse>() {
                coEvery { body<OpprettInnkrevingsOppdragResponse>().kravidentifikator } returns "2222-skeUUID"
            }
        }


        val dsMock = mockk<DatabaseService> {
            every { getAllUnsentKrav() } returns emptyList()
            every { saveAllNewKrav(any<List<KravLinje>>()) } answers { dataSource.connection.use { it.insertAllNewKrav(arg(0)) } }
            every { getSkeKravidentifikator(any<String>()) } answers { dataSource.connection.use { it.getSkeKravidentifikator(arg(0)) } }
            every { updateEndringWithSkeKravIdentifikator(any<String>(), any<String>()) } answers { dataSource.connection.use { it.updateEndringWithSkeKravIdentifikator(arg(0), arg(1)) } }
        }
        val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dsMock, ftpService = ftpService)
        val skeMock = spyk(skeService, recordPrivateCalls = true)

        justRun { skeMock["resendKrav"]() }

        skeMock.handleNewKrav()

        val kravEtter = dataSource.connection.getAllKrav()
        kravEtter.find { it.saksnummerNAV == "2223-navsaksnr" }?.saksnummerSKE shouldBe "2222-skeUUID"
        kravEtter.find { it.saksnummerNAV == "8889-navsaksnr" }?.saksnummerSKE shouldBe "8888-skeUUID"

    }

    test("Kravdata skal lagres med type som beskriver hva slags krav det er") {
        val dataSource = startContainer(this.testCase.name.testName, emptyList())
        val ftpService = FakeFtpService().setupMocks(Directories.INBOUND, listOf("AltOkFil.txt"))

        val dsMock = mockk<DatabaseService> {
            every { getAllUnsentKrav() } returns emptyList()
            every { saveAllNewKrav(any<List<KravLinje>>()) } answers { dataSource.connection.use { it.insertAllNewKrav(arg(0)) } }
            every { getSkeKravidentifikator(any<String>()) } answers { dataSource.connection.use { it.getSkeKravidentifikator(arg(0)) } }
            every { updateEndringWithSkeKravIdentifikator(any<String>(), any<String>()) } answers { dataSource.connection.use { it.updateEndringWithSkeKravIdentifikator(arg(0), arg(1)) } }
        }

        val skeClient = mockk<SkeClient>() {
            coEvery { getSkeKravidentifikator(any()) } returns mockk<HttpResponse> {
                coEvery { body<OpprettInnkrevingsOppdragResponse>().kravidentifikator } returns "foo"
                coEvery { status } returns HttpStatusCode.OK
            }
        }

        val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dsMock, ftpService = ftpService)
        val skeMock = spyk(skeService, recordPrivateCalls = true)

        justRun { skeMock["resendKrav"]() }

        skeMock.handleNewKrav()
        val lagredeKrav = dataSource.connection.getAllKrav()
        lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2
        lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2
        lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
    }


    test("Mottaksstatus skal oppdateres i database") {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(status = Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)
        val httpClient = setUpMockHttpClient(listOf(mottaksstatusKall))

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val dataSource = startContainer(this.testCase.name.testName, listOf("10NyeKrav.sql"))
        val databaseService = DatabaseService(PostgresDataSource(dataSource))
        val statusService = StatusService(skeClient, databaseService)

        statusService.hentOgOppdaterMottaksStatus()
        val kravdata = dataSource.connection.getAllKrav()

        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 10
    }

    test("Når et krav feiler skal det lagres i feilmeldingtabell") {
        val nyttKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.OPPRETT, HttpStatusCode.NotFound)
        val avskrivKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.NotFound)
        val endreRenterKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.NotFound)
        val endreHovedstolKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.NotFound)
        val endreReferanseKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.NotFound)
        val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

        val httpClient =
            setUpMockHttpClient(
                listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall),
            )
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocksWithMockEngine(listOf("10NyeKrav.txt"), this.testCase.name.testName, httpClient)


        mocks.first.handleNewKrav()
        val feilmeldinger = mocks.second.connection.prepareStatement("SELECT * FROM FEILMELDING").executeQuery().toFeilmelding()

        feilmeldinger.size shouldBe 10
        feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 404 }.size shouldBe 10

        val joinToString = feilmeldinger.joinToString("','") { it.corrId }.also(::println)
        val kravMedFeil =
            mocks.second.connection.prepareStatement(
                """select * from Krav where corr_id in ('$joinToString')""",
            ).executeQuery().toKrav()

        kravMedFeil.size shouldBe 10
        kravMedFeil.filter { it.status == Status.FANT_IKKE_SAKSREF_404.value }.size shouldBe 10
    }

    test("Hvis krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 så skal kravet resendes") {
        val ds = startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql"))

        ds.connection.getAllKrav().let { kravBefore ->
            kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
            kravBefore.filter { it.status == Status.ANNEN_SERVER_FEIL_500.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.UTILGJENGELIG_TJENESTE_503.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.INTERN_TJENERFEIL_500.value }.size shouldBe 1
        }

        val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
        val avskrivKravKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
        val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
        val endreHovedstolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
        val endreReferanseKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)
        val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

        val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall))

        val skeService = setupSkeServiceMockWithMockEngine(ds, httpClient)
        skeService.handleNewKrav()

        ds.connection.getAllKrav().let { kravAfter ->
            kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
            kravAfter.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 0
            kravAfter.filter { it.status == Status.ANNEN_SERVER_FEIL_500.value }.size shouldBe 0
            kravAfter.filter { it.status == Status.UTILGJENGELIG_TJENESTE_503.value }.size shouldBe 0
            kravAfter.filter { it.status == Status.INTERN_TJENERFEIL_500.value }.size shouldBe 0
        }
    }

})
