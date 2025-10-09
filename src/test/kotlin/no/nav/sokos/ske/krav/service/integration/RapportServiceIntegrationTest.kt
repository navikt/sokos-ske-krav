package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService

@OptIn(Frontend::class)
internal class RapportServiceIntegrationTest :
    FunSpec({
        extensions(DBListener)

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            DBListener.migrate("SQLscript/KravSomSkalAvstemmes.sql")
            DBListener.migrate("SQLscript/FeilmeldingerSomSkalAvstemmes.sql")

            val dbService = DatabaseService(DBListener.dataSource)
            dbService.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(dataSource = DBListener.dataSource, dbService = dbService)
            rapportService.oppdaterStatusTilRapportert(1)
            dbService.getAllKravForAvstemming().size shouldBe 2
        }
    })
