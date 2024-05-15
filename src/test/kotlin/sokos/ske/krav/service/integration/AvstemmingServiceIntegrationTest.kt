package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.apache.commons.net.ftp.FTP
import sokos.ske.krav.database.Repository.getAllKravForAvstemming
import sokos.ske.krav.database.Repository.getErrorMessageForKravId
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.setupSkeServiceMock
import sokos.ske.krav.util.startContainer

internal class AvstemmingServiceIntegrationTest: FunSpec ({


    test("visFeilFiler skal liste opp alle filer som ligger i Directories.FAILED"){
        val ftpService = FakeFtpService().setupMocks(Directories.FAILED, listOf("Fil-A.txt", "Fil-B.txt", "Fil-C.txt"))

        AvstemmingService(mockk<DatabaseService>(),  ftpService).visFeilFiler().run {
            this shouldContain "Filer som feilet"
            this shouldContain "<tr><td>Fil-A.txt</td</tr>"
            this shouldContain "<tr><td>Fil-B.txt</td</tr>"
            this shouldContain "<tr><td>Fil-C.txt</td</tr>"
        }
    }

    test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt"){
        val kravSomSkalAvstemmesDS = startContainer(this.testCase.name.testName, listOf("KravSomSkalAvstemmes.sql"))
        val dsMock = mockk<DatabaseService> {
            every { updateStatusForAvstemtKravToReported(any<Int>()) } answers { kravSomSkalAvstemmesDS.connection.updateStatusForAvstemtKravToReported(firstArg<Int>()) }
            every { getAllKravForAvstemming() } answers { kravSomSkalAvstemmesDS.connection.getAllKravForAvstemming() }
            every { getErrorMessageForKravId(any<Int>()) } answers { kravSomSkalAvstemmesDS.connection.getErrorMessageForKravId(firstArg<Int>())}
        }
        dsMock.getAllKravForAvstemming().size shouldBe 9

        val avstemmingService = AvstemmingService(dsMock, mockk<FtpService>())
        avstemmingService.oppdaterAvstemtKravTilRapportert(1)
        dsMock.getAllKravForAvstemming().size shouldBe 8
    }
})