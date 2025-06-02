package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.util.SftpListener
import no.nav.sokos.ske.krav.util.TestContainer
import no.nav.sokos.ske.krav.validation.FileValidator

private const val FILE_A = "Fil-A.txt"
private const val FILE_B = "Fil-B.txt"
private const val FILE_OK = "AltOkFil.txt"
private const val FILE_ERROR = "FilMedFeilAntallKrav.txt"

internal class FtpServiceIntegrationTest :
    BehaviorSpec({

        extensions(SftpListener)
        val testContainer = TestContainer()
        val dbService = DatabaseService(testContainer.dataSource)
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(mockk<SlackService>(relaxed = true)), databaseService = dbService)
        }

        Given("det finnes ubehandlede filer i \"inbound\" på FTP-serveren ") {
            clearAllDirectories()
            val fileList = listOf(FILE_OK, FILE_A, FILE_B, FILE_ERROR)
            SftpListener.putFiles(fileList, Directories.INBOUND)
            ftpService.getValidatedFiles()
            When("Validering er ok") {

                Then("Skal filen forbli i INBOUND") {
                    val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
                    successFilesInDir.size shouldBe 3
                    successFilesInDir shouldContain FILE_OK
                    successFilesInDir shouldContain FILE_A
                    successFilesInDir shouldContain FILE_B
                }
            }
            When("Validering ikke er ok") {
                Then("Skal filen flyttes til FAILED") {
                    val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
                    failedFilesInDir.size shouldBe 1
                    failedFilesInDir[0] shouldBe FILE_ERROR
                }
                And("Feilmelding skal lagres i database") {
                    dbService.getFileValidationMessage(FILE_ERROR).run {
                        size shouldBe 1
                        first().feilmelding shouldBe "${FileValidator.ErrorKeys.FEIL_I_ANTALL}: Antall krav: 16, Antall i siste linje: 101"
                    }
                }
            }
        }
        Given("listFiles kalles") {
            clearAllDirectories()
            listOf(Directories.INBOUND, Directories.OUTBOUND, Directories.FAILED).forEach { directory ->

                When("Directory er ${directory.name}") {

                    Then("Skal listFiles returnere filer i ${directory.name}") {
                        SftpListener.putFiles(listOf(FILE_A, FILE_B), directory)
                        val filesInDir = ftpService.listFiles(directory)
                        filesInDir.size shouldBe 2
                        filesInDir shouldContain FILE_A
                        filesInDir shouldContain FILE_B
                    }
                }
            }
        }
        Given("moveFile kalles") {
            clearAllDirectories()
            listOf(
                Pair(Directories.INBOUND, Directories.OUTBOUND),
                Pair(Directories.INBOUND, Directories.FAILED),
            ).forEach { (from, to) ->
                When("flytter fil fra ${from.name} til ${to.name}") {

                    Then("Skal filen flyttes fra ${from.name} til ${to.name}") {
                        SftpListener.putFiles(listOf(FILE_A), from)
                        ftpService.moveFile(FILE_A, from, to)
                        val filesInDir = ftpService.listFiles(to)
                        filesInDir.size shouldBe 1
                        filesInDir shouldContain FILE_A
                    }
                }
            }
        }
    })

private fun clearAllDirectories() {
    Directories.entries.forEach { directory ->
        SftpListener.clearDirectory(directory)
    }
}
