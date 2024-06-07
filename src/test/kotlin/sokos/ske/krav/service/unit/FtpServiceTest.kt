package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.listener.SftpListener
import sokos.ske.krav.listener.SftpListener.sftpProperties
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService

internal class FtpServiceTest: BehaviorSpec({

    extensions(listOf(SftpListener))

    val ftpService: FtpService by lazy {
        FtpService(sftpSession =  SftpConfig(sftpProperties).createSftpConnection())
    }

    Given("det finnes ubehandlede filer i \"inbound\" på FTP-serveren ") {
        SftpListener.putFiles(ftpService.session, listOf("AltOkFil.txt", "Fil-A.txt", "Fil-B.txt", "FilMedFeilIKontrollLinje.txt"), Directories.INBOUND,)
        When("Validering"){
           ftpService.getValidatedFiles()

        When("er ok"){
            Then("Skal filen forbli i inbound"){
                val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
                successFilesInDir.size shouldBe 3
                successFilesInDir shouldContain "AltOkFil.txt"
                successFilesInDir shouldContain "Fil-A.txt"
                successFilesInDir shouldContain "Fil-B.txt"
            }
        }
        When("ikke er ok"){
            Then("Skal filen flyttes til FAILED"){
                val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
                failedFilesInDir.size shouldBe 1
                failedFilesInDir[0] shouldBe "FilMedFeilIKontrollLinje.txt"
            }
        }
    }
    }
})
