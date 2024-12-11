package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.FileValidator

internal class FtpServiceIntegrationTest :
    BehaviorSpec({

        extensions(SftpListener)

        Given("det finnes ubehandlede filer i \"inbound\" på FTP-serveren ") {
            val testContainer = TestContainer()
            val dbService = DatabaseService(testContainer.dataSource)
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(SlackClient(client = MockHttpClient().getSlackClient())), databaseService = dbService)
            }

            SftpListener.putFiles(listOf("AltOkFil.txt", "Fil-A.txt", "Fil-B.txt", "FilMedFeilIKontrollLinje.txt"), Directories.INBOUND)
            When("Validering") {
                ftpService.getValidatedFiles()

                When("er ok") {
                    Then("Skal filen forbli i inbound") {
                        val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
                        successFilesInDir.size shouldBe 3
                        successFilesInDir shouldContain "AltOkFil.txt"
                        successFilesInDir shouldContain "Fil-A.txt"
                        successFilesInDir shouldContain "Fil-B.txt"
                    }
                }
                When("ikke er ok") {
                    Then("Skal filen flyttes til FAILED") {
                        val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
                        failedFilesInDir.size shouldBe 1
                        failedFilesInDir[0] shouldBe "FilMedFeilIKontrollLinje.txt"
                    }
                }
            }
        }
    })
