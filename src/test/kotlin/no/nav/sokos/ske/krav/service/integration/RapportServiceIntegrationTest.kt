package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.dbService
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService

@OptIn(Frontend::class)
internal class RapportServiceIntegrationTest :
    FunSpec({
        extensions(DBListener)

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            DBListener.loadInitScript("SQLscript/status/KravSomSkalAvstemmes.sql")
            DBListener.loadInitScript("SQLscript/feilmeldinger/FeilmeldingerSomSkalAvstemmes.sql")

            dbService.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(dataSource, dbService, feilmeldingRepository)
            rapportService.oppdaterStatusTilRapportert(1)
            dbService.getAllKravForAvstemming().size shouldBe 2
        }
    })
