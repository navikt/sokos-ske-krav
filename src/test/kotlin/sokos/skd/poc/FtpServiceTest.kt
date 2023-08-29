package sokos.skd.poc

import io.kotest.core.spec.style.FunSpec
import sokos.skd.poc.service.FtpService

internal class FtpServiceTest: FunSpec( {
    val ftpService = FtpService()

/*
    test("foo"){
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt"))

        val downloadedFiles = ftpService.downloadNewFiles(Directories.OUTBOUND)
        println("NUMBER OF FILES: ${downloadedFiles.size}")
        println("DOWNLOADED: ${downloadedFiles.map { it.name }}")


    }
    test("Move files"){
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt"))
        println("connected")
        val name = ftpService.listFiles(Directories.OUTBOUND).first()
        println("name: $name")

        val ftpFil = ftpService.downloadFtpFile(name, Directories.OUTBOUND)
        println("downloaded file: ${ftpFil.name}")
        ftpService.moveFiles(mutableListOf(ftpFil), Directories.OUTBOUND, Directories.FAILED)

        val failedFiles = ftpService.listFiles(Directories.FAILED)
        failedFiles.size shouldBe 1


    }

    test("Fail parsing"){
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt", "fil2.txt", "fil3.txt"))

        ftpService.downloadFtpFile("fil3.txt", Directories.OUTBOUND)

        val failedFiles = ftpService.listFiles(Directories.FAILED)
        failedFiles.size shouldBe 1
        failedFiles.first() shouldBe "fil3.txt"

    }

    test("bar"){
        ftpService.connect(Directories.OUTBOUND, listOf("fil1.txt"))

        ftpService.downloadFtpFile("fil1.txt", Directories.OUTBOUND)
    }
*/

})