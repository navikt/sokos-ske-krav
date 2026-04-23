package no.nav.sokos.ske.krav.service.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging
import mu.Marker

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.database.getAllKrav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.repository.KravRepository
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
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.avstemmingResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.httpErrorResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.innkrevingsOppdragEksistererIkkeResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.mottaksStatusResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.nyEndringResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.nyttKravResponse
import no.nav.sokos.ske.krav.util.mockHttpResponse
import no.nav.sokos.ske.krav.util.setupSkeServiceMock
import no.nav.sokos.ske.krav.util.setupSkeServiceMockWithMockEngine
import no.nav.sokos.ske.krav.validation.FileValidator

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)
        val logger =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
                every { warn(any<() -> Unit>()) } just runs
                every { error(any<() -> Unit>()) } just runs
                every { error(any<String>()) } just runs
                every { error(any<Marker>(), any<() -> Unit>()) } just runs
            }
        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns logger
        }
        afterSpec {
            unmockkObject(KotlinLogging)
        }
        beforeEach {
            circuitBreaker.reset()
        }
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(mockk<SlackService>(relaxed = true)), databaseService = mockk<DatabaseService>())
        }

        Given("Det finnes en fil i INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)
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
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator("8888-migrert") } returns
                        mockHttpResponse(200, """{"kravidentifikator": "avstemming8888-skeUUID"}""")

                    coEvery { getSkeKravidentifikator("2222-migrert") } returns
                        mockHttpResponse(200, """{"kravidentifikator": "avstemming2222-skeUUID"}""")
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
            SftpListener.putFiles(listOf("innsender/OppdragFil.txt"), Directories.INBOUND)
            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator(any()) } returns
                        mockHttpResponse(200, avstemmingResponse())
                    coEvery { getMottaksStatus(any(), any()) } returns
                        mockHttpResponse(200, mottaksStatusResponse(status = Status.RESKONTROFOERT.value))
                }
            val dbService = DatabaseService(DBListener.dataSource)
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)

            Then("skal type krav avgjøres og lagres") {
                skeService.handleNewKrav()
                val lagredeKrav = DBListener.dataSource.transaction { KravRepository.getAllKrav(it) }

                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
                // TODO: Why do we do that???
                DBListener.dataSource.transaction { tx ->
                    lagredeKrav.forEach { krav ->
                        updateStatus(tx, Status.RESKONTROFOERT, krav.corrId)
                    }
                }
//                lagredeKrav.forEach {
//                    DBListener.dataSource.connection.use { con ->
//                        con.updateStatus(Status.RESKONTROFOERT.value, it.corrId)
//                    }
//                }
            }
        }

        Given("Vi mottar 403 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avstemmingkall = MockResponse(Endpoint.AVSTEMMING, httpErrorResponse, HttpStatusCode.Forbidden)
            val endreRenterKall = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedStolKall = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(status = Status.RESKONTROFOERT.value), HttpStatusCode.OK)

            val httpClient =
                MockHttpClient.client(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingkall)
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource), slackService = slackServiceSpy)

            Then("Skal ingen feil lagres i feilmeldingtabell") {
                shouldThrow<CircuitBreakerException> {
                    skeService.handleNewKrav()
                }

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

                kravMedFeil.filter { it.status == Status.HTTP403_INGEN_TILGANG }.size shouldBe 0
            }
        }
        Given("Vi mottar 404 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avstemmingKall = MockResponse(Endpoint.AVSTEMMING, innkrevingsOppdragEksistererIkkeResponse(), HttpStatusCode.NotFound)
            val endreRenterKall = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedStolKall = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(status = Status.RESKONTROFOERT.value), HttpStatusCode.OK)

            val httpClient =
                MockHttpClient.client(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingKall)
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource), slackService = slackServiceSpy)

            Then("skal feilmelding sendes til Slack én gang per endring") {
                skeService.handleNewKrav()

                coVerify(exactly = 2) {
                    slackServiceSpy.addError(any(), any(), any<Pair<String, String>>())
                }

                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it
                            .prepareStatement("SELECT * FROM feilmelding")
                            .executeQuery()
                            .toFeilmelding()
                    }
                feilmeldinger.forEach {

                    it.melding.shouldContain(Regex("Innkrevingsoppdrag med referansenummerGammelSak .+ eksisterer ikke"))
                }
            }
        }

        Given("Et krav feiler ") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)

            val nyttKravResponse = MockResponse(Endpoint.OPPRETT, genericFeilResponse(), HttpStatusCode.UnprocessableEntity)
            val httpClient = MockHttpClient.client(nyttKravResponse)
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

                kravMedFeil.filter { it.status == Status.HTTP422_VALIDERINGSFEIL }.size shouldBe 10
            }
        }

        Given("To filer med krav legges i INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt", "krav/UtenFremtidigYtelse.txt"), Directories.INBOUND)

            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK)
            val httpClient = MockHttpClient.client(nyttKravKall, mottaksstatusKall)
            val dbService = DatabaseService(DBListener.dataSource)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, dbService)

            skeService.handleNewKrav()
            Then("skal det logges rett antall krav per fil") {
                verify(exactly = 1) {
                    logger.info("Fil: TiNyeKrav.txt - Nye: 10, Endringer: 0, Stopp: 0")
                    logger.info("Fil: UtenFremtidigYtelse.txt - Nye: 5, Endringer: 0, Stopp: 0")
                }
            }
        }

        Given("Et krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/status/KravSomSkalResendes.sql")

            DBListener.dataSource.connection.use { con ->
                con.getAllKrav().also { kravBefore ->
                    kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL }.size shouldBe 1
                }
            }

            val nyttKravResponse = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avskrivKravResponse = MockResponse(Endpoint.AVSKRIVING, nyEndringResponse(), HttpStatusCode.OK)
            val endreRenterResponse = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedstolResponse = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksStatusResponse = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK)

            val httpClient = MockHttpClient.client(nyttKravResponse, avskrivKravResponse, endreRenterResponse, endreHovedstolResponse, mottaksStatusResponse)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource))

            Then("skal kravet resendes") {
                skeService.handleNewKrav()
                DBListener.dataSource.connection.use { con ->
                    con.getAllKrav().also { kravAfter ->
                        kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE }.size shouldBe 0
                        kravAfter.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL }.size shouldBe 0
                    }
                }
            }
        }
    })
