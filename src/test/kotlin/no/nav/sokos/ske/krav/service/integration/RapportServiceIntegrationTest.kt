package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService

@OptIn(Frontend::class)
internal class RapportServiceIntegrationTest :
    FunSpec({
        extensions(DBListener)

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            DBListener.loadInitScripts("SQLscript/status/KravSomSkalAvstemmes.sql", "SQLscript/feilmeldinger/FeilmeldingerSomSkalAvstemmes.sql")

            kravRepository.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(feilmeldingRepository, kravRepository)
            rapportService.oppdaterStatusTilRapportert(1)
            kravRepository.getAllKravForAvstemming().size shouldBe 2
        }
    })
