package no.nav.sokos.ske.krav.service.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerException
import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
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
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses.avstemmingResponse
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses.httpErrorResponse
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses.innkrevingsOppdragEksistererIkkeResponse
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses.mottaksStatusResponse
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.mockHttpResponse
import no.nav.sokos.ske.krav.util.setUpMockHttpClient
import no.nav.sokos.ske.krav.util.setupSkeServiceMock
import no.nav.sokos.ske.krav.util.setupSkeServiceMockWithMockEngine
import no.nav.sokos.ske.krav.validation.FileValidator

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)

        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(mockk<SlackService>(relaxed = true)), databaseService = mockk<DatabaseService>())
        }

        beforeEach {
            circuitBreaker.reset()
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
            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avstemmingkall = MockRequestObj(httpErrorResponse, EndepunktType.AVSTEMMING, HttpStatusCode.Forbidden)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedStolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(status = Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient =
                setUpMockHttpClient(
                    listOf(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingkall),
                )
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

                kravMedFeil.filter { it.status == Status.HTTP403_INGEN_TILGANG.value }.size shouldBe 0
            }

            Then("Skal IKKE sende Slack-alarm") {
                coVerify(exactly = 0) {
                    slackServiceSpy.addError(
                        any<String>(),
                        any<String>(),
                        any<Pair<String, String>>(),
                    )
                }
            }
        }

        Given("Vi mottar 404 på avstemming av migrert krav") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            circuitBreaker.reset()
            val fileName = "krav/TestEndringMedAvstemmingAvKravident.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avstemmingkall = MockRequestObj(innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.AVSTEMMING, HttpStatusCode.NotFound)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedStolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient =
                setUpMockHttpClient(
                    listOf(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingkall),
                )
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource), slackService = slackServiceSpy)
            skeService.handleNewKrav()
            Then("Skal alert sendes til slack") {
                slot<String>()
                slot<String>()
                slot<Pair<String, String>>()
                val sendAlertFilenameList = mutableListOf<String>()
                val sendAlertHeaderList = mutableListOf<String>()
                val sendAlertMessagesList = mutableListOf<Pair<String, String>>()

                coVerify(exactly = 2) {
                    slackServiceSpy.addError(
                        capture(sendAlertFilenameList),
                        capture(sendAlertHeaderList),
                        capture(sendAlertMessagesList),
                    )
                }
                sendAlertFilenameList.forEach { it shouldBe fileName.substringAfter('/') }
                sendAlertHeaderList.forEach { it shouldBe "Feil fra SKE" }
                sendAlertMessagesList.size shouldBe 2
                sendAlertMessagesList.forEach { (header, _) ->
                    header shouldBe FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR
                }
            }

            Then("Skal feilen lagres i feilmeldingtabell") {
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it
                            .prepareStatement("SELECT * FROM feilmelding")
                            .executeQuery()
                            .toFeilmelding()
                    }

                feilmeldinger.filter { it.skeResponse.contains("404") }.size shouldBe 4
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
                kravMedFeil.filter { it.status == Status.HTTP404_FANT_IKKE_SAKSREF.value }.size shouldBe 4

                // Alle 4 rader skal ha HTTP404-status.
                val alleMigrertKrav =
                    DBListener.dataSource.connection
                        .use { it.getAllKrav() }
                        .filter { it.saksnummerNAV in listOf("2222-saksnrmig", "8888-saksnrmig") }
                alleMigrertKrav.size shouldBe 4
                alleMigrertKrav.filter { it.status == Status.HTTP404_FANT_IKKE_SAKSREF.value }.size shouldBe 4
            }
        }

        Given("Avstemming mot SKE returnerer HTTP 2xx men kravidentifikator mangler i responsen") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            circuitBreaker.reset()
            val fileName = "krav/TestEndringMedAvstemmingAvKravident.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avstemmingMed2xxMenUtenKravidentifikator = MockRequestObj("{}", EndepunktType.AVSTEMMING, HttpStatusCode.OK)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedStolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(status = Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingMed2xxMenUtenKravidentifikator))
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource))

            skeService.handleNewKrav()

            Then("Skal migrert krav IKKE få status KRAV_SENDT når kravidentifikator mangler i 2xx-responsen") {
                val kravEtter = DBListener.dataSource.connection.use { it.getAllKrav() }
                val migrertKrav = kravEtter.filter { it.saksnummerNAV in listOf("2222-saksnrmig", "8888-saksnrmig") }
                migrertKrav.size shouldBe 4
                migrertKrav.filter { it.status == Status.UKJENT_FEIL.value }.size shouldBe migrertKrav.size

                kravEtter.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe kravEtter.size - migrertKrav.size

                // handleErrors filtrerer på httpStatusCode.isSuccess(), ikke på status-enum-verdien.
                // Siden avstemming returnerte HTTP 200, er isSuccess() == true og feilmeldingen hoppes over –
                // selv om status-enum ble satt til UKJENT_FEIL. Ingen feilmelding skal opprettes.
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it.prepareStatement("SELECT * FROM feilmelding").executeQuery().toFeilmelding()
                    }
                feilmeldinger.size shouldBe 0
            }
        }

        Given("Avstemming mot SKE feiler med 422 for to migrerte krav") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            circuitBreaker.reset()
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avstemmingKall = MockRequestObj(Responses.genericFeilResponse(), EndepunktType.AVSTEMMING, HttpStatusCode.UnprocessableEntity)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedStolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(status = Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingKall))
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource), slackService = slackServiceSpy)

            skeService.handleNewKrav()

            Then("Skal feilmelding for hvert migrert krav lagres nøyaktig én gang, ikke dupliseres på tvers av iterasjoner") {
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it.prepareStatement("SELECT * FROM feilmelding").executeQuery().toFeilmelding()
                    }

                feilmeldinger.size shouldBe 4
            }

            Then("Skal IKKE sende Slack-alarm om manglende kravidentifikator") {
                coVerify(exactly = 0) {
                    slackServiceSpy.addError(
                        any(),
                        any(),
                        match<Pair<String, String>> { (title, _) -> title == FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR },
                    )
                }
                coVerify(atLeast = 1) {
                    slackServiceSpy.addError(any(), "Feil fra SKE", any<Pair<String, String>>())
                }
            }
        }

        Given("Avstemming mot SKE feiler med ikke-parsebar body (409 med tom JSON-kropp)") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            circuitBreaker.reset()
            SftpListener.putFiles(listOf("krav/TestEndringMedAvstemmingAvKravident.txt"), Directories.INBOUND)
            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            // {} er 2 tegn og kan ikke parses som FeilResponse – nøyaktig som i produksjonslogen ("input length=2")
            val avstemmingKall = MockRequestObj("{}", EndepunktType.AVSTEMMING, HttpStatusCode.Conflict)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedStolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(status = Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, endreRenterKall, endreHovedStolKall, mottaksstatusKall, avstemmingKall))
            val slackServiceSpy = spyk(SlackService(mockk<SlackClient>(relaxed = true)), recordPrivateCalls = true)
            val skeService = setupSkeServiceMockWithMockEngine(DBListener.dataSource, httpClient, ftpService, DatabaseService(DBListener.dataSource), slackService = slackServiceSpy)

            skeService.handleNewKrav()

            Then("Skal migrert krav få HTTP409-status") {
                val alleMigrertKrav =
                    DBListener.dataSource.connection
                        .use { it.getAllKrav() }
                        .filter { it.saksnummerNAV in listOf("2222-saksnrmig", "8888-saksnrmig") }
                alleMigrertKrav.size shouldBe 4
                alleMigrertKrav.filter { it.status == Status.HTTP409_ANNEN_KONFLIKT.value }.size shouldBe 4
            }

            Then("Skal feilmelding opprettes for hvert migrert krav selv om body ikke er parsebar som FeilResponse") {
                // defineStatusWithError lager nå alltid en custom fallback-FeilResponse når body ikke kan parses,
                // slik at saveErrorMessage og Slack-varsling alltid kjøres.
                val feilmeldinger =
                    DBListener.dataSource.connection.use {
                        it.prepareStatement("SELECT * FROM feilmelding").executeQuery().toFeilmelding()
                    }
                feilmeldinger.size shouldBe 4

                // saveErrorMessage har sin egen decode-fallback ("egendefinert") som er uavhengig av
                // defineStatusWithError sin fallback ("FEIL_FRA_SERVER"). Pinner innholdet her slik at
                // eventuelle fremtidige endringer i en av de to fallback-verdiene blir synlige.
                feilmeldinger.forEach { feilmelding ->
                    feilmelding.skeResponse shouldBe "{}"
                    feilmelding.melding shouldBe "{}" // detail fra "egendefinert"-fallbacken i saveErrorMessage
                }
            }

            Then("Skal sende generisk Slack-alarm, IKKE alarm om manglende kravidentifikator") {
                coVerify(atLeast = 1) {
                    slackServiceSpy.addError(any(), "Feil fra SKE", any<Pair<String, String>>())
                }
                coVerify(exactly = 0) {
                    slackServiceSpy.addError(
                        any(),
                        any(),
                        match<Pair<String, String>> { (title, _) -> title == FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR },
                    )
                }
            }
        }

        Given("Et krav feiler ") {
            DBListener.clearDB()
            SftpListener.putFiles(listOf("krav/TiNyeKrav.txt"), Directories.INBOUND)
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

            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
            val avskrivKravKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
            val endreHovedstolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
            val mottaksstatusKall = MockRequestObj(mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

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
    })
