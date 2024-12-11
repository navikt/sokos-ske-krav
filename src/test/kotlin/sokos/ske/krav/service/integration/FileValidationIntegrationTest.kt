package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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

class FileValidationIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        Given("En fil har en feil i kontroll-linjen") {
            val dbService = DatabaseService(TestContainer().dataSource)
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = dbService)
            }
            SftpListener.putFiles(listOf("FilMedFeilIKontrollLinje.txt"), Directories.INBOUND)
            When("Fil valideres") {
                ftpService.getValidatedFiles()
                Then("Skal feilen lagres i database ") {
                    val valideringsfeil = dbService.getFileValidationMessage("FilMedFeilIKontrollLinje.txt")
                    valideringsfeil.size shouldBe 2
                }
                And("Slack melding skal sendes") {
                    coVerify(exactly = 1) { slackClient.sendFilvalideringsMelding(any<String>(), any<List<Pair<String, String>>>()) }
                }
            }
        }
    })
