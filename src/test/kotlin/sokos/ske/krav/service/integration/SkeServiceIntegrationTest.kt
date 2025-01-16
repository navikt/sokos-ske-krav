package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.database.RepositoryExtensions.withParameters
import sokos.ske.krav.database.toFeilmelding
import sokos.ske.krav.database.toKrav
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.responses.AvstemmingResponse
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.domain.ske.responses.OpprettInnkrevingsOppdragResponse
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.MockHttpClientUtils.EndepunktType
import sokos.ske.krav.util.MockHttpClientUtils.MockRequestObj
import sokos.ske.krav.util.MockHttpClientUtils.Responses
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.setUpMockHttpClient
import sokos.ske.krav.util.setupSkeServiceMock
import sokos.ske.krav.util.setupSkeServiceMockWithMockEngine

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties), databaseService = mockk<DatabaseService>())
        }

        Given("Det finnes en fil i INBOUND") {
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
            val testContainer = TestContainer()
            val skeService = setupSkeServiceMock(databaseService = DatabaseService(testContainer.dataSource), ftpService = ftpService)

            Then("Skal alle validerte linjer lagres i database") {
                val kravbefore = testContainer.dataSource.connection.use { it.getAllKrav() }
                kravbefore.size shouldBe 0

                skeService.handleNewKrav()

                val kravEtter = testContainer.dataSource.connection.use { it.getAllKrav() }
                kravEtter.size shouldBe 10
            }
        }

        Given("Etter at kravene lagres i database skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra database") {
            val testContainer = TestContainer()
            testContainer.migrate("10NyeKrav.sql")
            SftpListener.putFiles(listOf("TestEndringKravident.txt"), Directories.INBOUND)

            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator("8888-navsaksnr") } returns
                        mockk<HttpResponse> {
                            coEvery { body<OpprettInnkrevingsOppdragResponse>().kravidentifikator } returns "8888-skeUUID"
                        }

                    coEvery { getSkeKravidentifikator("2222-navsaksnr") } returns
                        mockk<HttpResponse> {
                            coEvery { body<OpprettInnkrevingsOppdragResponse>().kravidentifikator } returns "2222-skeUUID"
                        }
                }

            val dbService = DatabaseService(testContainer.dataSource)
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)
            val skeMock = spyk(skeService, recordPrivateCalls = true)

            skeMock.handleNewKrav()

            val kravEtter = testContainer.dataSource.connection.use { it.getAllKrav() }
            kravEtter.find { it.saksnummerNAV == "2223-navsaksnr" }?.kravidentifikatorSKE shouldBe "2222-skeUUID"
            kravEtter.find { it.saksnummerNAV == "8889-navsaksnr" }?.kravidentifikatorSKE shouldBe "8888-skeUUID"
        }

        Given("Kravdata skal lagres med type som beskriver hva slags krav det er") {
            SftpListener.putFiles(listOf("AltOkFil.txt"), Directories.INBOUND)

            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator(any()) } returns
                        mockk<HttpResponse> {
                            coEvery { body<AvstemmingResponse>().kravidentifikator } returns "foo"
                            coEvery { status } returns HttpStatusCode.OK
                        }
                }
            val testContainer = TestContainer()
            val dbService = DatabaseService(testContainer.dataSource)
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)
            val skeMock = spyk(skeService, recordPrivateCalls = true)

            skeMock.handleNewKrav()
            val lagredeKrav = testContainer.dataSource.connection.use { it.getAllKrav() }
            lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
            lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2
            lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2
            lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
        }

        Given("Når et krav feiler skal det lagres i feilmeldingtabell") {
            val testContainer = TestContainer()
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.OPPRETT, HttpStatusCode.NotFound)
            val avskrivKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.NotFound)
            val endreRenterKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.NotFound)
            val endreHovedstolKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.NotFound)
            val endreReferanseKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.NotFound)
            val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall))
            val skeService = setupSkeServiceMockWithMockEngine(httpClient, ftpService, DatabaseService(testContainer.dataSource))

            skeService.handleNewKrav()
            val feilmeldinger =
                testContainer.dataSource.connection.use {
                    it
                        .prepareStatement("SELECT * FROM feilmelding")
                        .executeQuery()
                        .toFeilmelding()
                }

            feilmeldinger.size shouldBe 10
            feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 404 }.size shouldBe 10

            val kravMedFeil =
                testContainer.dataSource.connection
                    .use { con ->
                        feilmeldinger.map { feilmelding ->
                            con
                                .prepareStatement("""select * from krav where corr_id = ?""")
                                .withParameters(feilmelding.corrId)
                                .executeQuery()
                                .toKrav()
                        }
                    }.flatten()

            kravMedFeil.size shouldBe 10
            kravMedFeil.filter { it.status == Status.HTTP404_FANT_IKKE_SAKSREF.value }.size shouldBe 10
        }

        Given("Hvis krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 så skal kravet resendes") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalResendes.sql")

            testContainer.dataSource.connection.use { con ->
                con.getAllKrav().also { kravBefore ->
                    kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 1
                }
            }

            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avskrivKravKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedstolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val endreReferanseKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall))

            val skeService = setupSkeServiceMockWithMockEngine(httpClient, ftpService, DatabaseService(testContainer.dataSource))
            skeService.handleNewKrav()
            testContainer.dataSource.connection.use { con ->
                con.getAllKrav().also { kravAfter ->
                    kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
                    kravAfter.filter { it.status == Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 0
                    kravAfter.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 0
                    kravAfter.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 0
                    kravAfter.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 0
                }
            }
        }
    })
