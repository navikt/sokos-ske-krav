package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coVerify
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.FileValidator

internal class FileValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val dbService = DatabaseService(TestContainer().dataSource)

        Given("En fil har feil antall krav i kontroll-linjen") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = dbService)
            }
            val filename = "FilMedFeilAntallKrav.txt"
            SftpListener.putFiles(listOf(filename), Directories.INBOUND)

            When("Fil valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    with(dbService.getFileValidationMessage(filename)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe filename
                            feilmelding shouldContain FileValidator.ErrorKeys.FEIL_I_ANTALL
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_DATO
                        }
                    }
                }
                // TODO: Capture?
                And("Slack melding skal sendes") {
                    coVerify(exactly = 1) { slackClient.sendFilvalideringsMelding(any<String>(), any<List<Pair<String, String>>>()) }
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = dbService)
            }
            val filename = "FilMedFeilSum.txt"
            SftpListener.putFiles(listOf(filename), Directories.INBOUND)

            When("Fil valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    with(dbService.getFileValidationMessage(filename)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe filename
                            feilmelding shouldContain FileValidator.ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_ANTALL
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_DATO
                        }
                    }
                }
                And("Slack melding skal sendes") {
                    coVerify(exactly = 1) { slackClient.sendFilvalideringsMelding(any<String>(), any<List<Pair<String, String>>>()) }
                }
            }
        }

        Given("En fil har forskjellige datier i kontroll-linjene") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = dbService)
            }
            val filename = "FilMedFeilSendtDato.txt"
            SftpListener.putFiles(listOf(filename), Directories.INBOUND)

            When("Fil valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    with(dbService.getFileValidationMessage(filename)) {
                        size shouldBe 1
                        with(first()) {
                            filnavn shouldBe filename
                            feilmelding shouldContain FileValidator.ErrorKeys.FEIL_I_DATO
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_SUM
                            feilmelding shouldNotContain FileValidator.ErrorKeys.FEIL_I_ANTALL
                        }
                    }
                }
                And("Slack melding skal sendes") {
                    coVerify(exactly = 1) { slackClient.sendFilvalideringsMelding(any<String>(), any<List<Pair<String, String>>>()) }
                }
            }
        }

        Given("En fil har alle typer feil") {
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = dbService)
            }
            val filename = "FilMedAlleTyperFeilForFilValidering.txt"
            SftpListener.putFiles(listOf(filename), Directories.INBOUND)

            When("Fil valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilene lagres i database ") {
                    with(dbService.getFileValidationMessage(filename)) {
                        size shouldBe 3
                        count { it.filnavn == filename } shouldBe 3
                        count { it.feilmelding.contains(FileValidator.ErrorKeys.FEIL_I_DATO) } shouldBe 1
                        count { it.feilmelding.contains(FileValidator.ErrorKeys.FEIL_I_SUM) } shouldBe 1
                        count { it.feilmelding.contains(FileValidator.ErrorKeys.FEIL_I_ANTALL) } shouldBe 1
                    }
                }
                And("Slack meldinger skal sendes") {
                    coVerify(exactly = 1) { slackClient.sendFilvalideringsMelding(any<String>(), any<List<Pair<String, String>>>()) }
                }
            }
        }
    })
