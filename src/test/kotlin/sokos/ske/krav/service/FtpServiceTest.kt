package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.FakeFtpService

internal class FtpServiceTest : FunSpec({


	test("Ingen filer funnet") {
		val fakeFtpService = FakeFtpService()
		val ftpService =
			fakeFtpService.setupMocks(Directories.INBOUND, emptyList())

		val files = ftpService.getValidatedFiles()

		files.size shouldBe 0
	}
	test("Fail parsing") {
		val fakeFtpService = FakeFtpService()
		val ftpService =
			fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt", "fil2.txt", "test.NAVI", "fil3.txt"))

		val successFiles = ftpService.getValidatedFiles()
		successFiles.size shouldBe 3

		val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
		failedFilesInDir.size shouldBe 1
		failedFilesInDir[0] shouldBe "fil3.txt"

		val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
		successFilesInDir.size shouldBe 3
		successFilesInDir shouldContain "test.NAVI"
		successFilesInDir shouldContain "fil1.txt"
		successFilesInDir shouldContain "fil2.txt"


		fakeFtpService.close()
	}

})