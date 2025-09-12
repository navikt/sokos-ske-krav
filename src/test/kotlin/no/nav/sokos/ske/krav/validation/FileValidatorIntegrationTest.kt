package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec

internal class FileValidatorIntegrationTest :
    BehaviorSpec({
//         TODO: Gjør det her på SkeServiceTest
//        extensions(SftpListener, PostgresListener, WiremockListener)
//
//        val ftpService: FtpService by lazy {
//            FtpService(SftpConfig(SftpListener.sftpProperties))
//        }
//
//        val slackService: SlackService by lazy {
//            val slackClientSpy = spyk(SlackClient(slackEndpoint = WiremockListener.wiremock.baseUrl()))
//            spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
//        }
//
//        val validateringRepository: ValideringsfeilRepository by lazy {
//            ValideringsfeilRepository(PostgresListener.dataSource)
//        }
//
//        val skeService: SkeService by lazy {
//            SkeService(
//                dataSource = PostgresListener.dataSource,
//                slackService = slackService,
//                ftpService = ftpService
//            )
//        }
//
//        Given("Fil er OK") {
//            val fileName = "AltOkFil.txt"
//            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
//
//            When("Filen valideres") {
//
//
//                Then("Skal ingen feil lagres i database") {
//                    validateringRepository.getValideringsFeilForFil(fileName).size shouldBe 0
//                }
//
//                And("Alert skal ikke sendes") {
//                    coVerify(exactly = 0) {
//                        slackService.addError(any<String>(), any<String>(), any<List<Pair<String, String>>>())
//                    }
//                }
//            }
//        }
//
//        Given("En fil har feil antall linjer i kontroll-linjen") {
//            val slackServiceSpy = setupSlackService()
//            val ftpService = setupFtpService(slackServiceSpy)
//
//            val fileName = "FilMedFeilAntallKrav.txt"
//            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
//
//            When("Filen valideres") {
//                ftpService.getValidatedFiles()
//
//                Then("Skal feilen lagres i database") {
//                    with(dbService.getFileValidationMessage(fileName)) {
//                        size shouldBe 1
//                        with(first()) {
//                            filnavn shouldBe fileName
//                            feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO
//                        }
//                    }
//                }
//                And("Alert skal sendes til slack") {
//                    val sendAlertFilenameSlot = slot<String>()
//                    val sendAlertHeaderSlot = slot<String>()
//                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()
//
//                    coVerify(exactly = 1) {
//                        slackServiceSpy.addError(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
//                    }
//                    sendAlertFilenameSlot.captured shouldBe fileName
//                    sendAlertHeaderSlot.captured shouldBe "Feil i validering av fil"
//                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
//                    capturedSendAlertMessages.size shouldBe 1
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_ANTALL }.size shouldBe 1
//                }
//            }
//        }
//
//        Given("En fil har feil sum i kontroll-linjen") {
//            val slackServiceSpy = setupSlackService()
//            val ftpService = setupFtpService(slackServiceSpy)
//            val fileName = "FilMedFeilSum.txt"
//            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
//
//            When("Filen valideres") {
//                ftpService.getValidatedFiles()
//
//                Then("Skal feilen lagres i database ") {
//                    with(dbService.getFileValidationMessage(fileName)) {
//                        size shouldBe 1
//                        with(first()) {
//                            filnavn shouldBe fileName
//                            feilmelding shouldContain ErrorKeys.FEIL_I_SUM
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO
//                        }
//                    }
//                }
//                And("Alert skal sendes til slack") {
//                    val sendAlertFilenameSlot = slot<String>()
//                    val sendAlertHeaderSlot = slot<String>()
//                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()
//
//                    coVerify(exactly = 1) {
//                        slackServiceSpy.addError(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
//                    }
//                    sendAlertFilenameSlot.captured shouldBe fileName
//                    sendAlertHeaderSlot.captured shouldBe "Feil i validering av fil"
//                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
//                    capturedSendAlertMessages.size shouldBe 1
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_SUM }.size shouldBe 1
//                }
//            }
//        }
//
//        Given("En fil har forskjellige datoer i kontroll-linjene") {
//            val slackServiceSpy = setupSlackService()
//            val ftpService = setupFtpService(slackServiceSpy)
//            val fileName = "FilMedFeilUtbetalDato.txt"
//            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
//
//            When("Filen valideres") {
//                ftpService.getValidatedFiles()
//
//                Then("Skal feilen lagres i database ") {
//                    with(dbService.getFileValidationMessage(fileName)) {
//                        size shouldBe 1
//                        with(first()) {
//                            filnavn shouldBe fileName
//                            feilmelding shouldContain ErrorKeys.FEIL_I_DATO
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM
//                            feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL
//                        }
//                    }
//                }
//                And("Alert skal sendes til slack") {
//                    val sendAlertFilenameSlot = slot<String>()
//                    val sendAlertHeaderSlot = slot<String>()
//                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()
//
//                    coVerify(exactly = 1) {
//                        slackServiceSpy.addError(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
//                    }
//                    sendAlertFilenameSlot.captured shouldBe fileName
//                    sendAlertHeaderSlot.captured shouldBe "Feil i validering av fil"
//                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
//                    capturedSendAlertMessages.size shouldBe 1
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_DATO }.size shouldBe 1
//                }
//            }
//        }
//
//        Given("En fil har alle typer feil") {
//            val slackServiceSpy = setupSlackService()
//            val ftpService = setupFtpService(slackServiceSpy)
//            val fileName = "FilMedAlleTyperFeilForFilValidering.txt"
//            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
//
//            When("Filen valideres") {
//                ftpService.getValidatedFiles()
//
//                Then("Skal feilene lagres i database ") {
//                    with(dbService.getFileValidationMessage(fileName)) {
//                        size shouldBe 3
//                        count { it.filnavn == fileName } shouldBe 3
//                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_DATO) } shouldBe 1
//                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_SUM) } shouldBe 1
//                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_ANTALL) } shouldBe 1
//                    }
//                }
//                And("Alert skal sendes til slack") {
//                    val sendAlertFilenameSlot = slot<String>()
//                    val sendAlertHeaderSlot = slot<String>()
//                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()
//
//                    coVerify(exactly = 1) {
//                        slackServiceSpy.addError(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
//                    }
//                    sendAlertFilenameSlot.captured shouldBe fileName
//                    sendAlertHeaderSlot.captured shouldBe "Feil i validering av fil"
//                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
//                    capturedSendAlertMessages.size shouldBe 3
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_DATO }.size shouldBe 1
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_SUM }.size shouldBe 1
//                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_ANTALL }.size shouldBe 1
//                }
//            }
//        }
    })
