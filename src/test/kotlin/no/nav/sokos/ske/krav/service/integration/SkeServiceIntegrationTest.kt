package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec

internal class SkeServiceIntegrationTest :
    BehaviorSpec({
//        extensions(SftpListener)
//        val ftpService: FtpService by lazy {
//            FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(mockk<SlackService>(relaxed = true)), databaseService = mockk<DatabaseService>())
//        }
//        val testContainer = TestContainer()
//        testContainer.migrate("SQLscript/10NyeKrav.sql")
//
//        Given("Det finnes en fil i INBOUND") {
//            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
//            val skeService = setupSkeServiceMock(databaseService = DatabaseService(testContainer.dataSource), ftpService = ftpService)
//
//            Then("Skal alle validerte linjer lagres i database") {
//                val kravbefore = testContainer.dataSource.connection.use { it.getAllKrav() }
//                skeService.behandleSkeKrav()
//                val kravEtter = testContainer.dataSource.connection.use { it.getAllKrav() }
//                kravEtter.size shouldBe 10 + kravbefore.size
//            }
//        }
//
//        Given("Det kommer endringer eller avskrivinger") {
//            SftpListener.putFiles(listOf("TestEndringKravident.txt"), Directories.INBOUND)
//            val skeClient =
//                mockk<SkeClient> {
//                    coEvery { getSkeKravidentifikator("8888-migrert") } returns
//                        mockk<HttpResponse> {
//                            coEvery { body<AvstemmingResponse>() } returns AvstemmingResponse("avstemming8888-skeUUID")
//                            coEvery { status } returns HttpStatusCode.OK
//                        }
//
//                    coEvery { getSkeKravidentifikator("2222-migrert") } returns
//                        mockk<HttpResponse> {
//                            coEvery { body<AvstemmingResponse>() } returns AvstemmingResponse("avstemming2222-skeUUID")
//                            coEvery { status } returns HttpStatusCode.OK
//                        }
//                }
//
//            val dbService = DatabaseService(testContainer.dataSource)
//            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)
//
//            val kravBefore = testContainer.dataSource.connection.getAllKrav()
//            with(kravBefore.find { it.saksnummerNAV == "2222-navsaksnr" }) {
//                this?.kravidentifikatorSKE shouldBe "2222-skeUUID"
//                this?.referansenummerGammelSak shouldBe ""
//            }
//            with(kravBefore.find { it.saksnummerNAV == "8888-navsaksnr" }) {
//                this?.kravidentifikatorSKE shouldBe "8888-skeUUID"
//                this?.referansenummerGammelSak shouldBe ""
//            }
//
//            kravBefore.find { it.saksnummerNAV == "2222-migrert" } shouldBe null
//            kravBefore.find { it.saksnummerNAV == "8888-migrert" } shouldBe null
//
//            skeService.behandleSkeKrav()
//
//            When("Kravet finnes i database") {
//                Then("skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra database") {
//                    val kravEtter = testContainer.dataSource.connection.use { it.getAllKrav() }
//                    kravEtter.find { it.saksnummerNAV == "2223-navsaksnr" }?.kravidentifikatorSKE shouldBe "2222-skeUUID"
//                    kravEtter.find { it.saksnummerNAV == "8889-navsaksnr" }?.kravidentifikatorSKE shouldBe "8888-skeUUID"
//                }
//            }
//            When("Det er et migrert krav") {
//                Then("skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra kall til SKE avstemming") {
//                    val kravEtter = testContainer.dataSource.connection.use { it.getAllKrav() }
//                    kravEtter.find { it.saksnummerNAV == "2222-saksnrmig" }?.kravidentifikatorSKE shouldBe "avstemming2222-skeUUID"
//                    kravEtter.find { it.saksnummerNAV == "8888-saksnrmig" }?.kravidentifikatorSKE shouldBe "avstemming8888-skeUUID"
//                }
//            }
//        }
//
//        Given("Et krav skal lagres i database") {
//            SftpListener.putFiles(listOf("AltOkFil.txt"), Directories.INBOUND)
//
//            val skeClient =
//                mockk<SkeClient> {
//                    coEvery { getSkeKravidentifikator(any()) } returns
//                        mockk<HttpResponse> {
//                            coEvery { body<AvstemmingResponse>().kravidentifikator } returns "foo"
//                            coEvery { status } returns HttpStatusCode.OK
//                        }
//                    coEvery { getMottaksStatus(any(), any()) } returns
//                        mockk<HttpResponse> {
//                            coEvery { body<MottaksStatusResponse>().mottaksStatus } returns "RESKONTROFOERT"
//                            coEvery { status } returns HttpStatusCode.OK
//                        }
//                }
//            val dbService = DatabaseService(testContainer.dataSource)
//            val skeService = setupSkeServiceMock(skeClient = skeClient, databaseService = dbService, ftpService = ftpService)
//            val kravbefore = testContainer.dataSource.connection.use { it.getAllKrav() }
//
//            Then("skal type krav avgjøres og lagres") {
//                skeService.behandleSkeKrav()
//                val lagredeKrav = testContainer.dataSource.connection.use { it.getAllKrav() }
//                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2 + kravbefore.filter { it.kravtype == STOPP_KRAV }.size
//                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 2 + kravbefore.filter { it.kravtype == ENDRING_RENTE }.size
//                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 2 + kravbefore.filter { it.kravtype == ENDRING_HOVEDSTOL }.size
//                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97 + kravbefore.filter { it.kravtype == NYTT_KRAV }.size
//                lagredeKrav.forEach {
//                    testContainer.dataSource.connection.use { con ->
//                        con.updateStatus("RESKONTROFOERT", it.corrId)
//                    }
//                }
//            }
//        }
//
//        Given("Et krav feiler ") {
//            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)
//            val nyttKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.OPPRETT, HttpStatusCode.NotFound)
//            val avskrivKravKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.NotFound)
//            val endreRenterKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.NotFound)
//            val endreHovedstolKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.NotFound)
//            val endreReferanseKall = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.NotFound)
//            val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)
//
//            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall))
//            val skeService = setupSkeServiceMockWithMockEngine(httpClient, ftpService, DatabaseService(testContainer.dataSource))
//
//            val feilmeldingerBefore =
//                testContainer.dataSource.connection.use {
//                    it
//                        .prepareStatement("SELECT * FROM feilmelding")
//                        .executeQuery()
//                        .toFeilmelding()
//                }
//
//            Then("skal det lagres i feilmeldingtabell") {
//                skeService.behandleSkeKrav()
//                val feilmeldinger =
//                    testContainer.dataSource.connection.use {
//                        it
//                            .prepareStatement("SELECT * FROM feilmelding")
//                            .executeQuery()
//                            .toFeilmelding()
//                    }
//
//                feilmeldinger.size shouldBe 10 + feilmeldingerBefore.size
//                feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 404 }.size shouldBe 10
//
//                val kravMedFeil =
//                    testContainer.dataSource.connection
//                        .let { con ->
//                            feilmeldinger.map { feilmelding ->
//                                con
//                                    .prepareStatement("""select * from krav where corr_id = ?""")
//                                    .withParameters(feilmelding.corrId)
//                                    .executeQuery()
//                                    .toKrav()
//                            }
//                        }.flatten()
//
//                kravMedFeil.size shouldBe 10
//                kravMedFeil.filter { it.status == Status.HTTP404_FANT_IKKE_SAKSREF.value }.size shouldBe 10
//            }
//        }
//
//        Given("Et krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500") {
//            testContainer.migrate("SQLscript/KravSomSkalResendes.sql")
//
//            testContainer.dataSource.connection.use { con ->
//                con.getAllKrav().also { kravBefore ->
//                    kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 3
//                    kravBefore.filter { it.status == Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
//                    kravBefore.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 1
//                    kravBefore.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 1
//                    kravBefore.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 1
//                }
//            }
//
//            val nyttKravKall = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
//            val avskrivKravKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
//            val endreRenterKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
//            val endreHovedstolKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
//            val endreReferanseKall = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)
//            val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)
//
//            val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall))
//            val skeService = setupSkeServiceMockWithMockEngine(httpClient, ftpService, DatabaseService(testContainer.dataSource))
//
//            Then("skal kravet resendes") {
//                skeService.behandleSkeKrav()
//                testContainer.dataSource.connection.use { con ->
//                    con.getAllKrav().also { kravAfter ->
//                        kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
//                        kravAfter.filter { it.status == Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 0
//                        kravAfter.filter { it.status == Status.HTTP500_ANNEN_SERVER_FEIL.value }.size shouldBe 0
//                        kravAfter.filter { it.status == Status.HTTP503_UTILGJENGELIG_TJENESTE.value }.size shouldBe 0
//                        kravAfter.filter { it.status == Status.HTTP500_INTERN_TJENERFEIL.value }.size shouldBe 0
//                    }
//                }
//            }
//        }
    })
