package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Frontend
import sokos.ske.krav.service.RapportService
import sokos.ske.krav.util.TestContainer

@OptIn(Frontend::class)
internal class AvstemmingServiceIntegrationTest :
    FunSpec({

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            val testContainer = TestContainer()
            testContainer.migrate("SQLscript/KravSomSkalAvstemmes.sql")
            testContainer.migrate("SQLscript/FeilmeldingerSomSkalAvstemmes.sql")

            val dbService = DatabaseService(testContainer.dataSource)
            dbService.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(dbService)
            rapportService.oppdaterStatusTilRapportert(1)
            dbService.getAllKravForAvstemming().size shouldBe 2
        }
    })
