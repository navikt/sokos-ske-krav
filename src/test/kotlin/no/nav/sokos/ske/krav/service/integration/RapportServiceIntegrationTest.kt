package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.transaction

@OptIn(Frontend::class)
internal class RapportServiceIntegrationTest :
    FunSpec({
        extensions(DBListener)

        beforeSpec {
            DBListener.loadInitScripts("SQLscript/status/KravSomSkalAvstemmes.sql", "SQLscript/feilmeldinger/FeilmeldingerSomSkalAvstemmes.sql")
        }

        test("oppdaterAvstemtKravTilRapportert skal sette status til rapportert og hente tabelldata på nytt") {
            kravRepository.getAllKravForAvstemming().size shouldBe 3

            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)
            rapportService.oppdaterStatusTilRapportert(1)
            kravRepository.getAllKravForAvstemming().size shouldBe 2
        }

        test("oppdaterStatusTilIkkeSendt skal sette kravstatus til KRAV_IKKE_SENDT for valgt krav-id") {
            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)

            dataSource.transaction { session ->
                kravRepository.getAllKrav(session).first { it.kravId == 1L }.status shouldBe Status.KRAV_SENDT
            }

            rapportService.oppdaterStatusTilIkkeSendt(1)

            dataSource.transaction { session ->
                kravRepository.getAllKrav(session).first { it.kravId == 1L }.status shouldBe Status.KRAV_IKKE_SENDT
            }
        }

        test("releasePendingKrav skal frigjøre pending endring og stopp til korrekt kravtype") {
            dataSource.transaction { session ->
                session.execute(queryOf("update krav set status = ?, kravtype = ? where id = ?", Status.PENDING_ENDRING_HOVEDSTOL.value, "ENDRING_HOVEDSTOL", 1))
                session.execute(queryOf("update krav set status = ?, kravtype = ? where id = ?", Status.PENDING_ENDRING_RENTE.value, "ENDRING_RENTE", 2))
                session.execute(queryOf("update krav set status = ?, kravtype = ? where id = ?", Status.PENDING_STOPP.value, "STOPP_KRAV", 3))
            }
            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)

            rapportService.releasePendingKrav(1)
            rapportService.releasePendingKrav(2)
            rapportService.releasePendingKrav(3)

            dataSource.transaction { session ->
                val krav = kravRepository.getAllKrav(session)
                krav.first { it.kravId == 1L }.status shouldBe Status.KRAV_IKKE_SENDT
                krav.first { it.kravId == 2L }.status shouldBe Status.KRAV_IKKE_SENDT
                krav.first { it.kravId == 3L }.status shouldBe Status.KRAV_IKKE_SENDT
                krav.first { it.kravId == 1L }.kravtype shouldBe "ENDRING_HOVEDSTOL"
                krav.first { it.kravId == 2L }.kravtype shouldBe "ENDRING_RENTE"
                krav.first { it.kravId == 3L }.kravtype shouldBe "STOPP_KRAV"
            }
        }
    })
