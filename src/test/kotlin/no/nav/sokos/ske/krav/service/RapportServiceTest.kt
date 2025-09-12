package no.nav.sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.PostgresListener

@OptIn(Frontend::class)
internal class RapportServiceTest :
    FunSpec({
        extensions(PostgresListener)

        val rapportService: RapportService by lazy {
            RapportService(PostgresListener.dataSource)
        }

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            PostgresListener.migrate("SQLscript/KravSomSkalAvstemmes.sql")
            PostgresListener.migrate("SQLscript/FeilmeldingerSomSkalAvstemmes.sql")

            rapportService.oppdaterStatusTilRapportert(1)
            PostgresListener.kravRepository.getAllKravForAvstemming().size shouldBe 2
        }
    })
