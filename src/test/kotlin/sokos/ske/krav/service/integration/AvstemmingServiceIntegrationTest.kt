package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.containers.SftpContainer
import sokos.ske.krav.util.containers.SftpListener
import sokos.ske.krav.util.containers.TestContainer

internal class AvstemmingServiceIntegrationTest :
    FunSpec({
        extensions(SftpListener)

        val ftpService: FtpService by lazy {
            FtpService(sftpSession = SftpConfig(SftpContainer.sftpProperties).createSftpConnection())
        }

        test("visFeilFiler skal liste opp alle filer som ligger i Directories.FAILED") {
            SftpContainer.putFiles(ftpService.session, listOf("Fil-A.txt", "Fil-B.txt", "Fil-C.txt"), Directories.FAILED)
            AvstemmingService(mockk<DatabaseService>(), ftpService).visFeilFiler().run {
                this shouldContain "Filer som feilet"
                this shouldContain "<tr><td>Fil-A.txt</td</tr>"
                this shouldContain "<tr><td>Fil-B.txt</td</tr>"
                this shouldContain "<tr><td>Fil-C.txt</td</tr>"
            }
        }

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalAvstemmes.sql")

            val dbService = DatabaseService(testContainer.dataSource)
            dbService.getAllKravForAvstemming().size shouldBe 9

            val avstemmingService = AvstemmingService(dbService, ftpService)
            avstemmingService.oppdaterAvstemtKravTilRapportert(1)
            dbService.getAllKravForAvstemming().size shouldBe 8
        }
    })