package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal class FtpServiceTest: FunSpec( {

    test("Antall filer stemmer"){
        val fakeFtpService = FakeFtpService()
        fakeFtpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt"))

        val files = fakeFtpService.getFiles(::fileValidator, Directories.OUTBOUND)
        println("NUMBER OF FILES: ${files.size}")
        println("DOWNLOADED: ${files.map { it.name }}")
        fakeFtpService.close()
    }

    test("Fail parsing"){
        val fakeFtpService = FakeFtpService()
        fakeFtpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt", "fil3.txt"))
        val files = fakeFtpService.getFiles(::fileValidator)
        files.size shouldBe 2

        val failedFilesInDir = fakeFtpService.listFiles(Directories.FAILED)
        failedFilesInDir.size shouldBe 1
        failedFilesInDir[0] shouldBe "fil3.txt"

        val okFilesInDir = fakeFtpService.listFiles(Directories.OUTBOUND)
        okFilesInDir.size shouldBe 2
        okFilesInDir[0] shouldBe "fil1.txt"
        okFilesInDir[1] shouldBe "fil2.txt"

        fakeFtpService.close()
    }

})