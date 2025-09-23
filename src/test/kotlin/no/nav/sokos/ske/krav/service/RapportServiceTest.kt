package no.nav.sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.session
import no.nav.sokos.ske.krav.repository.KravRepository

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
            KravRepository.getAllKravForAvstemming(session).size shouldBe 2
        }
    })
