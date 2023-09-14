package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal class FtpServiceTest: FunSpec( {

    test("Antall filer stemmer"){
        val ftpService = FtpService()
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt"))

        val files = ftpService.getFiles(::fileValidator, Directories.OUTBOUND)
        println("NUMBER OF FILES: ${files.size}")
        println("DOWNLOADED: ${files.map { it.name }}")
        ftpService.close()
    }

    test("Fail parsing"){
        val ftpService = FtpService()
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt", "fil3.txt"))
        val files = ftpService.getFiles(::fileValidator)
        files.size shouldBe 2

        val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
        failedFilesInDir.size shouldBe 1
        failedFilesInDir[0] shouldBe "fil3.txt"

        val okFilesInDir = ftpService.listFiles(Directories.OUTBOUND)
        okFilesInDir.size shouldBe 2
        okFilesInDir[0] shouldBe "fil1.txt"
        okFilesInDir[1] shouldBe "fil2.txt"

        ftpService.close()
    }

})