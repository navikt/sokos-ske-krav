package no.nav.sokos.ske.krav.service.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.getAllFeilmeldinger
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.getKravForFeilmeldinger
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.avstemmingResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.httpErrorResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.innkrevingsOppdragEksistererIkkeResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.mottaksStatusResponse
import no.nav.sokos.ske.krav.util.mockHttpResponse
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

        fun dbService() = DatabaseService(DBListener.dataSource)

        fun slackServiceSpy() = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)

        fun skeServiceWithMockEngine(
            httpClient: io.ktor.client.HttpClient,
            slackService: SlackService = SlackService(SlackClient(client = MockHttpClient.slackClient)),
        ) = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, dbService(), slackService = slackService)

        Given("Det finnes en fil i INBOUND") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)
            val skeService = setupSkeServiceMock(databaseService = dbService(), ftpService = ftpService)

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
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService(), ftpService = ftpService)
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
            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService(), ftpService = ftpService)

            Then("skal type krav avgjøres og lagres") {
                skeService.handleNewKrav()
                val lagredeKrav = DBListener.dataSource.connection.use { it.getAllKrav() }
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
                lagredeKrav.forEach {
                    DBListener.dataSource.connection.use { con ->
                        con.updateStatus(Status.RESKONTROFOERT.value, it.corrId)
                    }
                }
            }
        }

        Given("Vi mottar 403 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val httpClient = MockHttpClient.allOk(MockResponse(Endpoint.AVSTEMMING, httpErrorResponse, HttpStatusCode.Forbidden))
            val slackSpy = slackServiceSpy()
            val skeService = skeServiceWithMockEngine(httpClient, slackService = slackSpy)

            Then("Skal ingen feil lagres i feilmeldingtabell") {
                shouldThrow<CircuitBreakerException> {
                    skeService.handleNewKrav()
                }

                val feilmeldinger = DBListener.dataSource.connection.use { it.getAllFeilmeldinger() }

                feilmeldinger.filter { it.skeResponse.contains("403") }.size shouldBe 0
                val kravMedFeil = DBListener.dataSource.connection.use { it.getKravForFeilmeldinger(feilmeldinger) }

                kravMedFeil.filter { it.status == Status.HTTP403_INGEN_TILGANG.value }.size shouldBe 0
            }
        }
        Given("Vi mottar 404 på avstemming") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val httpClient = MockHttpClient.allOk(MockResponse(Endpoint.AVSTEMMING, innkrevingsOppdragEksistererIkkeResponse(), HttpStatusCode.NotFound))
            val slackSpy = slackServiceSpy()
            val skeService = skeServiceWithMockEngine(httpClient, slackService = slackSpy)

            Then("skal feilmelding sendes til Slack én gang per endring") {
                skeService.handleNewKrav()

                coVerify(exactly = 2) {
                    slackSpy.addError(any(), any(), any<Pair<String, String>>())
                }

                val feilmeldinger = DBListener.dataSource.connection.use { it.getAllFeilmeldinger() }
                feilmeldinger.forEach {
                    it.melding.shouldContain(Regex("Innkrevingsoppdrag med referansenummerGammelSak .+ eksisterer ikke"))
                }
            }
        }

        Given("Et krav feiler ") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)
            val httpClient = MockHttpClient.client(MockResponse(Endpoint.OPPRETT, genericFeilResponse(), HttpStatusCode.UnprocessableEntity))
            val skeService = skeServiceWithMockEngine(httpClient)

            Then("skal det lagres i feilmeldingtabell") {
                skeService.handleNewKrav()
                val feilmeldinger = DBListener.dataSource.connection.use { it.getAllFeilmeldinger() }

                feilmeldinger.filter { it.error == "422" }.size shouldBe 10
                val kravMedFeil = DBListener.dataSource.connection.use { it.getKravForFeilmeldinger(feilmeldinger) }

                kravMedFeil.filter { it.status == Status.HTTP422_VALIDERINGSFEIL.value }.size shouldBe 10
            }
        }

        Given("Et krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/status/KravSomSkalResendes.sql")

            DBListener.dataSource.connection.use { con ->
                con.getAllKrav().also { kravBefore ->
                    kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
                    kravBefore.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 1
                    kravBefore.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 1
                }
            }

            val skeService = skeServiceWithMockEngine(MockHttpClient.allOk())

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
    })
