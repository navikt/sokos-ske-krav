package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.ErrorKeys
import no.nav.sokos.ske.krav.validation.FileValidator

private const val FILE_OK = "AllValideringOk.txt"
private const val FILE_ERROR = "validering/filvalidering/FeilAntallKrav.txt"

private val FILE_ERROR_NAME = FILE_ERROR.substringAfterLast("/")

internal class FtpServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)

        val ftpService: FtpService by lazy {
            FtpService(
                dataSource = DBListener.dataSource,
                sftpConfig = SftpConfig(SftpListener.sftpProperties),
                fileValidator = FileValidator(),
                filValideringsfeilRepository = filvalideringsFeilRepository,
                slackService = mockk<SlackService>(relaxed = true),
            )
        }

        Given("det finnes ubehandlede filer i \"inbound\" på FTP-serveren ") {
            clearAllDirectories()
            val fileList = listOf(FILE_OK, FILE_ERROR)
            SftpListener.putFiles(fileList, Directories.INBOUND)
            ftpService.getValidatedFiles()
            When("Validering er ok") {

                Then("Skal filen forbli i INBOUND") {
                    val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
                    successFilesInDir.size shouldBe 1
                    successFilesInDir shouldContain FILE_OK
                }
            }
            When("Validering ikke er ok") {
                Then("Skal filen flyttes til FAILED") {
                    val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
                    failedFilesInDir.size shouldBe 1
                    failedFilesInDir[0] shouldBe FILE_ERROR_NAME
                }
                And("Feilmelding skal lagres i database") {
                    val filValideringsfeil =
                        DBListener.dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, FILE_ERROR_NAME)
                        }
                    filValideringsfeil.shouldHaveSize(1)
                    filValideringsfeil.first().feilmelding shouldBe "${ErrorKeys.FEIL_I_ANTALL.value}: Antall krav: 16, Antall i siste linje: 101"
                }
            }
        }
        Given("listFiles kalles") {
            clearAllDirectories()
            listOf(Directories.INBOUND, Directories.OUTBOUND, Directories.FAILED).forEach { directory ->

                When("Directory er ${directory.name}") {

                    Then("Skal listFiles returnere filer i ${directory.name}") {
                        SftpListener.putFiles(listOf(FILE_OK), directory)
                        val filesInDir = ftpService.listFiles(directory)
                        filesInDir.size shouldBe 1
                        filesInDir shouldContain FILE_OK
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
                        SftpListener.putFiles(listOf(FILE_OK), from)
                        ftpService.moveFile(FILE_OK, from, to)
                        val filesInDir = ftpService.listFiles(to)
                        filesInDir.size shouldBe 1
                        filesInDir shouldContain FILE_OK
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
