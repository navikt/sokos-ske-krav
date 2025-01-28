package sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.FileValidator.ErrorKeys

internal class FileValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val dbService = DatabaseService(TestContainer().dataSource)

        Given("Fil er OK") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), slackClient = slackClient, databaseService = dbService)
            }
            val fileName = "AltOkFil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dbService.getFileValidationMessage(fileName).size shouldBe 0
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackClient.sendMessage(any<String>(), any<String>(), any<List<Pair<String, String>>>())
                    }
                }
            }
        }
        Given("En fil har feil antall linjer i kontroll-linjen") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), slackClient = slackClient, databaseService = dbService)
            }

            val fileName = "FilMedFeilAntallKrav.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database") {
                    with(dbService.getFileValidationMessage(fileName)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe fileName
                            feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO
                        }
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 1
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_ANTALL }.size shouldBe 1
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), slackClient = slackClient, databaseService = dbService)
            }
            val fileName = "FilMedFeilSum.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    with(dbService.getFileValidationMessage(fileName)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe fileName
                            feilmelding shouldContain ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO
                        }
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 1
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_SUM }.size shouldBe 1
                }
            }
        }

        Given("En fil har forskjellige datoer i kontroll-linjene") {

            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), slackClient = slackClient, databaseService = dbService)
            }
            val fileName = "FilMedFeilSendtDato.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    with(dbService.getFileValidationMessage(fileName)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe fileName
                            feilmelding shouldContain ErrorKeys.FEIL_I_DATO
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL
                        }
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 1
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_DATO }.size shouldBe 1
                }
            }
        }

        Given("En fil har alle typer feil") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), slackClient = slackClient, databaseService = dbService)
            }
            val fileName = "FilMedAlleTyperFeilForFilValidering.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilene lagres i database ") {
                    with(dbService.getFileValidationMessage(fileName)) {
                        size shouldBe 3
                        count { it.filnavn == fileName } shouldBe 3
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_DATO) } shouldBe 1
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_SUM) } shouldBe 1
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_ANTALL) } shouldBe 1
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: List<Pair<String, String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 3
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_DATO }.size shouldBe 1
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_SUM }.size shouldBe 1
                    capturedSendAlertMessages.filter { it.first == ErrorKeys.FEIL_I_ANTALL }.size shouldBe 1
                }
            }
        }
    })
