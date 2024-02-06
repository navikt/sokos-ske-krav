package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import sokos.ske.krav.util.FakeFtpService

internal class FtpServiceTest : FunSpec({

	test("OK filer skal ikke flyttes") {
		val fakeFtpService = FakeFtpService()
		val ftpService =
			fakeFtpService.setupMocks(Directories.INBOUND, listOf("AltOkFil.txt", "AltOkFil2.txt", "test.NAVI"))

		val successFiles = ftpService.getValidatedFiles()
		successFiles.size shouldBe 3

		val successFilesInDir = ftpService.listFiles(Directories.INBOUND)
		successFilesInDir.size shouldBe 3
		successFilesInDir shouldContain "test.NAVI"
		successFilesInDir shouldContain "AltOkFil.txt"
		successFilesInDir shouldContain "AltOkFil2.txt"


		fakeFtpService.close()
	}

  test("Filer som feiler validering skal flyttes til Directories.FAILED") {
	val fakeFtpService = FakeFtpService()
	val ftpService =
	  fakeFtpService.setupMocks(Directories.INBOUND, listOf("FilMedFeilIKontrollLinje.txt"))
	
	ftpService.getValidatedFiles().size shouldBe 0

	val failedFilesInDir = ftpService.listFiles(Directories.FAILED)
	failedFilesInDir.size shouldBe 1
	failedFilesInDir[0] shouldBe "FilMedFeilIKontrollLinje.txt"

	fakeFtpService.close()
  }

})