package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.FakeFtpService


internal class FtpServiceTest: FunSpec( {
    

    test("Fail parsing"){
        val fakeFtpService = FakeFtpService()
        val ftpService =  fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt", "fil2.txt", "test.NAVI", "fil3.txt"))

        val successFiles = ftpService.getValidatedFiles(::fileValidator)
        successFiles.size shouldBe 3

        val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
        failedFilesInDir.size shouldBe 1
        failedFilesInDir[0] shouldBe "fil3.txt"

        val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
        successFilesInDir.size shouldBe 3
        successFilesInDir[0] shouldBe "test.NAVI"
        successFilesInDir[1] shouldBe "fil1.txt"
        successFilesInDir[2] shouldBe "fil2.txt"


        fakeFtpService.close()
    }

})