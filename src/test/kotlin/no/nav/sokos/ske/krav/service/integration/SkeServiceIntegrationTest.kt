package no.nav.sokos.ske.krav.service.integration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import org.slf4j.LoggerFactory

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.Status.HTTP403_INGEN_TILGANG
import no.nav.sokos.ske.krav.domain.Status.KRAV_SENDT
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.avskrivKravResponse
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
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.FileValidator

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)
        val skeServiceLogger = LoggerFactory.getLogger(SkeService::class.java) as Logger
        val logAppender = ListAppender<ILoggingEvent>()

        beforeEach {
            circuitBreaker.reset()
            logAppender.list.clear()
        }
        val ftpService: FtpService by lazy {
            FtpService(
                dataSource = dataSource,
                sftpConfig = SftpConfig(SftpListener.sftpProperties),
                fileValidator = FileValidator(mockk<SlackService>(relaxed = true)),
                filValideringsfeilRepository = filvalideringsFeilRepository,
            )
        }

        beforeTest {
            logAppender.start()
            skeServiceLogger.addAppender(logAppender)
        }
        afterTest {
            skeServiceLogger.detachAppender(logAppender)
            logAppender.stop()
        }

        Given("Det finnes en fil i INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)
            val skeService =
                setupSkeServiceMock(
                    ftpService = ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("Skal alle validerte linjer lagres i database") {
                skeService.handleNewKrav()
                val allKrav =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKrav.shouldHaveSize(10)
            }
        }

        Given("Det kommer endringer eller avskrivinger") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val skeClient =
                mockk<SkeClient> {
                    coEvery { getSkeKravidentifikator("8888-migrert") } returns
                        mockHttpResponse(200, """{"kravidentifikator": "avstemming8888-skeUUID"}""")

                    coEvery { getSkeKravidentifikator("2222-migrert") } returns
                        mockHttpResponse(200, """{"kravidentifikator": "avstemming2222-skeUUID"}""")
                }
            val skeService =
                setupSkeServiceMock(
                    skeClient = skeClient,
                    ftpService = ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )
            val kravBefore =
                dataSource.transaction { session ->
                    kravRepository.getAllKrav(session)
                }
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
                    val kravEtter =
                        dataSource.transaction { session ->
                            kravRepository.getAllKrav(session)
                        }
                    kravEtter.find { it.saksnummerNAV == "2223-navsaksnr" }?.kravidentifikatorSKE shouldBe "2222-skeUUID"
                    kravEtter.find { it.saksnummerNAV == "8889-navsaksnr" }?.kravidentifikatorSKE shouldBe "8888-skeUUID"
                }
            }
            When("Det er et migrert krav") {
                Then("skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra kall til SKE avstemming") {
                    val kravEtter =
                        dataSource.transaction { session ->
                            kravRepository.getAllKrav(session)
                        }
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
            val skeService =
                setupSkeServiceMock(
                    skeClient = skeClient,
                    ftpService = ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("skal type krav avgjøres og lagres") {
                skeService.handleNewKrav()
                val allKrav =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKrav.groupBy { it.kravtype }.apply {
                    get(STOPP_KRAV)?.shouldHaveSize(2)
                    get(ENDRING_RENTE)?.shouldHaveSize(2)
                    get(ENDRING_HOVEDSTOL)?.shouldHaveSize(2)
                    get(NYTT_KRAV)?.shouldHaveSize(97)
                }
            }
        }

        Given("Response fra SKE er OK") {
            DBListener.clearDB()
            DBListener.loadInitScripts(
                "SQLscript/krav/ToNyeKrav.sql",
                "SQLscript/krav/ToEndredeKrav.sql",
                "SQLscript/krav/ToStoppedeKrav.sql",
            )

            val kravSomSkalSendes = kravRepository.getAllUnsentKrav()
            kravSomSkalSendes.shouldHaveSize(8)
            kravSomSkalSendes.groupBy { it.kravtype }.apply {
                get(NYTT_KRAV)?.shouldHaveSize(2)
                get(ENDRING_RENTE)?.shouldHaveSize(2)
                get(ENDRING_HOVEDSTOL)?.shouldHaveSize(2)
                get(STOPP_KRAV)?.shouldHaveSize(2)
            }

            circuitBreaker.reset()
            val kravidentifikatorSke = "4321"

            val opprettResponse = MockResponse(Endpoint.OPPRETT, nyttKravResponse(kravidentifikatorSke), HttpStatusCode.OK)
            val endreRenterResponse = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedstolResponse = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val avskrivingResponse = MockResponse(Endpoint.AVSKRIVING, avskrivKravResponse(), HttpStatusCode.OK)

            val httpClient = MockHttpClient.client(opprettResponse, endreRenterResponse, endreHovedstolResponse, avskrivingResponse)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            skeService.handleNewKrav()
            val allKrav =
                dataSource
                    .transaction { session ->
                        kravRepository.getAllKrav(session)
                    }.groupBy { it.kravtype }

            Then("Skal de nye kravene oppdateres med SKE kravidentifikator og status sendt") {
                allKrav[NYTT_KRAV]?.run {
                    shouldHaveSize(2)
                    count { it.saksnummerNAV == "1111-navsaksnr" } shouldBe 1
                    count { it.saksnummerNAV == "2222-navsaksnr" } shouldBe 1
                    count { it.kravidentifikatorSKE == kravidentifikatorSke } shouldBe 2
                    forAll { it.status shouldBe KRAV_SENDT }
                }
            }

            Then("Skal de endre kravene oppdateres med status sendt") {
                allKrav[ENDRING_RENTE]?.run {
                    shouldHaveSize(2)
                    forAll { it.status shouldBe KRAV_SENDT }
                }

                allKrav[ENDRING_HOVEDSTOL]?.run {
                    shouldHaveSize(2)
                    forAll { it.status shouldBe KRAV_SENDT }
                }
            }

            Then("Skal de stopp kravene oppdateres med status sendt") {
                allKrav[STOPP_KRAV]?.run {
                    shouldHaveSize(2)
                    forAll { it.status shouldBe KRAV_SENDT }
                }
            }

            kravRepository.getAllUnsentKrav().shouldBeEmpty()
        }

        Given("Vi mottar 403 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avstemmingkall = MockResponse(Endpoint.AVSTEMMING, httpErrorResponse, HttpStatusCode.Forbidden)
            val endreRenterKall = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedStolKall = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(status = Status.RESKONTROFOERT.value), HttpStatusCode.OK)
            val httpClient =
                MockHttpClient.client(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingkall)
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    slackService = slackServiceSpy,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("Skal ingen feil lagres i feilmeldingtabell") {
                shouldThrow<CircuitBreakerException> {
                    skeService.handleNewKrav()
                }

                feilmeldingRepository.getAllFeilmeldinger().filter { it.skeResponse.contains("403") }.shouldBeEmpty()
                val allKrav =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKrav.forNone { it.status shouldBe HTTP403_INGEN_TILGANG }
            }
        }

        Given("Vi mottar 404 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avstemmingKall = MockResponse(Endpoint.AVSTEMMING, innkrevingsOppdragEksistererIkkeResponse(), HttpStatusCode.NotFound)
            val endreRenterKall = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedStolKall = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(status = Status.RESKONTROFOERT.value), HttpStatusCode.OK)

            val httpClient =
                MockHttpClient.client(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingKall)
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    slackService = slackServiceSpy,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("skal feilmelding sendes til Slack én gang per endring") {
                skeService.handleNewKrav()

                coVerify(exactly = 2) {
                    slackServiceSpy.addError(any(), any(), any<Pair<String, String>>())
                }

                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
                feilmeldinger.forAll {
                    it.melding shouldContain Regex("Innkrevingsoppdrag med referansenummerGammelSak .+ eksisterer ikke")
                }
            }
        }

        Given("Et krav feiler ") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)

            val nyttKravResponse = MockResponse(Endpoint.OPPRETT, genericFeilResponse(), HttpStatusCode.UnprocessableEntity)
            val httpClient = MockHttpClient.client(nyttKravResponse)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("skal det lagres i feilmeldingtabell") {
                skeService.handleNewKrav()
                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
                feilmeldinger.filter { it.error == "422" }.shouldHaveSize(10)
                val allKrav =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKrav.filter { it.status == Status.HTTP422_VALIDERINGSFEIL }.shouldHaveSize(10)
            }
        }

        Given("To filer med krav leses fra INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt", "krav/UtenFremtidigYtelse.txt"), Directories.INBOUND)

            val nyttKravKall = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val mottaksstatusKall = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK)
            val httpClient = MockHttpClient.client(nyttKravKall, mottaksstatusKall)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("skal det logges rett antall krav per fil") {
                skeService.handleNewKrav()

                val messages = logAppender.list.map { it.formattedMessage }
                messages.filter { it == "Fil: TiNyeKrav.txt - Nye: 10, Endringer: 0, Stopp: 0" }.shouldHaveSize(1)
                messages.filter { it == "Fil: UtenFremtidigYtelse.txt - Nye: 5, Endringer: 0, Stopp: 0" }.shouldHaveSize(1)
            }
        }

        Given("Et krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/status/KravSomSkalResendes.sql")
            val allKravBefore =
                dataSource.transaction { session ->
                    kravRepository.getAllKrav(session)
                }
            allKravBefore.groupBy { it.status }.apply {
                get(Status.KRAV_IKKE_SENDT)?.shouldHaveSize(3)
                get(Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND)?.shouldHaveSize(3)
                get(Status.HTTP500_ANNEN_SERVER_FEIL)?.shouldHaveSize(1)
                get(Status.HTTP500_INTERN_TJENERFEIL)?.shouldHaveSize(1)
                get(Status.HTTP503_UTILGJENGELIG_TJENESTE)?.shouldHaveSize(1)
            }

            val nyttKravResponse = MockResponse(Endpoint.OPPRETT, nyttKravResponse(), HttpStatusCode.OK)
            val avskrivKravResponse = MockResponse(Endpoint.AVSKRIVING, nyEndringResponse(), HttpStatusCode.OK)
            val endreRenterResponse = MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
            val endreHovedstolResponse = MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
            val mottaksStatusResponse = MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK)

            val httpClient = MockHttpClient.client(nyttKravResponse, avskrivKravResponse, endreRenterResponse, endreHovedstolResponse, mottaksStatusResponse)
            val skeService =
                setupSkeServiceMockWithMockEngine(
                    httpClient,
                    ftpService,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    feilmeldingRepository = feilmeldingRepository,
                    kravRepository = kravRepository,
                )

            Then("skal kravet resendes") {
                skeService.handleNewKrav()
                val allKravAfter =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKravAfter.groupBy { it.status }.apply {
                    get(Status.KRAV_IKKE_SENDT).shouldBeNull()
                    get(Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND).shouldBeNull()
                    get(Status.HTTP500_ANNEN_SERVER_FEIL).shouldBeNull()
                    get(Status.HTTP500_INTERN_TJENERFEIL).shouldBeNull()
                    get(Status.HTTP503_UTILGJENGELIG_TJENESTE).shouldBeNull()
                }
            }
        }

        Given("Det finnes stangende krav i database") {

            When("Det ikke finnes noen stangende krav i database") {
                DBListener.clearDB()
                Then("Skal det ikke logges") {
                    setupSkeServiceMock(kravRepository = kravRepository).checkForStangendeKrav()
                    logAppender.list.map { it.formattedMessage }.shouldBeEmpty()
                }
            }
            When("Stangende krav er fra ett system") {
                DBListener.clearDB()
                logAppender.list.clear()
                DBListener.loadInitScripts("SQLscript/krav/ToStangendeKravFraEttSystem.sql")

                Then("Skal det kun telles for det systemet") {
                    setupSkeServiceMock(kravRepository = kravRepository).checkForStangendeKrav()
                    val message = logAppender.list.map { it.formattedMessage }.single()
                    message shouldContain "2 krav er blitt forsøkt resendt i over 24 timer"
                    message shouldContain "2 krav fra OB04 har blitt forsøkt resendt i 1 dag(er)"
                }
            }

            When("Stangende krav er fra flere system") {
                DBListener.clearDB()
                logAppender.list.clear()
                DBListener.loadInitScripts("SQLscript/krav/FireStangendeKravFraTreSystemer.sql")

                Then("Skal det telles per system") {
                    setupSkeServiceMock(kravRepository = kravRepository).checkForStangendeKrav()
                    val message = logAppender.list.map { it.formattedMessage }.single()
                    message shouldContain "4 krav er blitt forsøkt resendt i over 24 timer"
                    message shouldContain "2 krav fra OB04 har blitt forsøkt resendt i 1 dag(er)"
                    message shouldContain "1 krav fra ARENA har blitt forsøkt resendt i 5 dag(er)"
                    message shouldContain "1 krav fra INFOTRYGD har blitt forsøkt resendt i 3 dag(er)"
                }
            }
        }
    })
