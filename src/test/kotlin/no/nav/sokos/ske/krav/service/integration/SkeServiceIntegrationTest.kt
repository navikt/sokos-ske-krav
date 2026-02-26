package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.responses.AvstemmingResponse
import no.nav.sokos.ske.krav.dto.ske.responses.MottaksStatusResponse
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.withParameters
import no.nav.sokos.ske.krav.repository.toFeilmelding
import no.nav.sokos.ske.krav.repository.toKrav
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.EndepunktType
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.MockRequestObj
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.setUpMockHttpClient
import no.nav.sokos.ske.krav.util.setupSkeServiceMock
import no.nav.sokos.ske.krav.util.setupSkeServiceMockWithMockEngine
import no.nav.sokos.ske.krav.validation.FileValidator

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)
        beforeEach {
            circuitBreaker.reset()
        }
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(mockk<SlackService>(relaxed = true)), databaseService = mockk<DatabaseService>())
        }

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        Given("Det finnes en fil i INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
            val skeService = setupSkeServiceMock(databaseService = DatabaseService(DBListener.dataSource), ftpService = ftpService)

            Then("Skal alle validerte linjer lagres i database") {
                skeService.handleNewKrav()
                DBListener.dataSource.connection
                    .use { it.getAllKrav() }
                    .size shouldBe 10
            }
        }

        Given("Det kommer endringer eller avskrivinger") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/10NyeKrav.sql")
            SftpListener.putFiles(listOf("TestEndringKravident.txt"), Directories.INBOUND)
            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator("8888-migrert") } returns
                        mockk<HttpResponse> {
                            coEvery { body<AvstemmingResponse>() } returns AvstemmingResponse("avstemming8888-skeUUID")
                            coEvery { status } returns HttpStatusCode.OK
                        }

                    coEvery { getSkeKravidentifikator("2222-migrert") } returns
                        mockk<HttpResponse> {
                            coEvery { body<AvstemmingResponse>() } returns AvstemmingResponse("avstemming2222-skeUUID")
                            coEvery { status } returns HttpStatusCode.OK
                        }
                }

            val dbService = DatabaseService(DBListener.dataSource)
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)

            val kravBefore = DBListener.dataSource.connection.use { it.getAllKrav() }
            with(kravBefore.find { it.saksnummerNAV == "2222-navsaksnr" }) {
                this?.kravidentifikatorSKE shouldBe "2222-skeUUID"
                this?.referansenummerGammelSak shouldBe ""
            }
            with(kravBefore.find { it.saksnummerNAV == "8888-navsaksnr" }) {
                this?.kravidentifikatorSKE shouldBe "8888-skeUUID"
                this?.referansenummerGammelSak shouldBe ""
            }

            kravBefore.find { it.saksnummerNAV == "2222-migrert" } shouldBe null
            kravBefore.find { it.saksnummerNAV == "8888-migrert" } shouldBe null

            skeService.handleNewKrav()

            When("Kravet finnes i database") {
                Then("skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra database") {
                    val kravEtter = DBListener.dataSource.connection.use { it.getAllKrav() }
                    kravEtter.find { it.saksnummerNAV == "2223-navsaksnr" }?.kravidentifikatorSKE shouldBe "2222-skeUUID"
                    kravEtter.find { it.saksnummerNAV == "8889-navsaksnr" }?.kravidentifikatorSKE shouldBe "8888-skeUUID"
                }
            }
            When("Det er et migrert krav") {
                Then("skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra kall til SKE avstemming") {
                    val kravEtter = DBListener.dataSource.connection.use { it.getAllKrav() }
                    kravEtter.find { it.saksnummerNAV == "2222-saksnrmig" }?.kravidentifikatorSKE shouldBe "avstemming2222-skeUUID"
                    kravEtter.find { it.saksnummerNAV == "8888-saksnrmig" }?.kravidentifikatorSKE shouldBe "avstemming8888-skeUUID"
                }
            }
        }

        Given("Et krav skal lagres i database") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("AltOkFil.txt"), Directories.INBOUND)

            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator(any()) } returns
                        mockk<HttpResponse> {
                            coEvery { body<AvstemmingResponse>().kravidentifikator } returns "foo"
                            coEvery { status } returns HttpStatusCode.OK
                        }
                    coEvery { getMottaksStatus(any(), any()) } returns
                        mockk<HttpResponse> {
                            coEvery { body<MottaksStatusResponse>().mottaksStatus } returns "RESKONTROFOERT"
                            coEvery { status } returns HttpStatusCode.OK
                        }
                }
            val dbService = DatabaseService(DBListener.dataSource)
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)

            Then("skal type krav avgjøres og lagres") {
                skeService.handleNewKrav()
                val lagredeKrav = DBListener.dataSource.connection.use { it.getAllKrav() }
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
                lagredeKrav.forEach {
                    DBListener.dataSource.connection.use { con ->
                        con.updateStatus("RESKONTROFOERT", it.corrId)
                    }
                }
            }
        }

        Given("Vi mottar 403") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.httpErrorResponse, EndepunktType.OPPRETT, HttpStatusCode.Forbidden)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall))
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource))

            Then("Skal ingen feil lagres i feilmeldingtabell") {
                skeService.handleNewKrav()
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it
                            .prepareStatement("SELECT * FROM feilmelding")
                            .executeQuery()
                            .toFeilmelding()
                    }

                feilmeldinger.filter { it.skeResponse.contains("403") }.size shouldBe 0
                val kravMedFeil =
                    DBListener.dataSource.connection.use { conn ->
                        feilmeldinger.flatMap { feilmelding ->
                            conn
                                .prepareStatement("""select * from krav where corr_id = ?""")
                                .withParameters(feilmelding.corrId)
                                .executeQuery()
                                .toKrav()
                        }
                    }

                kravMedFeil.filter { it.status == Status.HTTP403_INGEN_TILGANG.value }.size shouldBe 0
            }
        }
        Given("Et krav feiler ") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.genericFeilResponse(), EndepunktType.OPPRETT, HttpStatusCode.UnprocessableEntity)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall))
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource))

            Then("skal det lagres i feilmeldingtabell") {
                skeService.handleNewKrav()
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it
                            .prepareStatement("SELECT * FROM feilmelding")
                            .executeQuery()
                            .toFeilmelding()
                    }

                feilmeldinger.filter { it.error == "422" }.size shouldBe 10

                val kravMedFeil =
                    DBListener.dataSource.connection.use { conn ->
                        feilmeldinger.flatMap { feilmelding ->
                            conn
                                .prepareStatement("""select * from krav where corr_id = ?""")
                                .withParameters(feilmelding.corrId)
                                .executeQuery()
                                .toKrav()
                        }
                    }

                kravMedFeil.filter { it.status == Status.HTTP422_VALIDERINGSFEIL.value }.size shouldBe 10
            }
        }

        Given("Et krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/KravSomSkalResendes.sql")

            DBListener.dataSource.connection.use { con ->
                con.getAllKrav().also { kravBefore ->
                    kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 1
                }
            }

            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avskrivKravKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedstolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, mottaksstatusKall))
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource))

            Then("skal kravet resendes") {
                skeService.handleNewKrav()
                DBListener.dataSource.connection.use { con ->
                    con.getAllKrav().also { kravAfter ->
                        kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 0
                    }
                }
            }
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }
    })
