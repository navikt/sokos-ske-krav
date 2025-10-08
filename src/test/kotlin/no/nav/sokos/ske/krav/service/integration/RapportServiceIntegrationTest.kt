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

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            val dbListener = DBListener()
            dbListener.migrate("SQLscript/KravSomSkalAvstemmes.sql")
            dbListener.migrate("SQLscript/FeilmeldingerSomSkalAvstemmes.sql")

            val dbService = DatabaseService(dbListener.dataSource)
            dbService.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(dbService)
            rapportService.oppdaterStatusTilRapportert(1)
            dbService.getAllKravForAvstemming().size shouldBe 2
        }
    })
