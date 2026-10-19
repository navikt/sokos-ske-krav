package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.transaction

@OptIn(Frontend::class)
internal class RapportServiceIntegrationTest :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.clearDB()
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

        // En operatør må ikke kunne gjøre en pending-rad sendbar før det opprettede oppryddingskravet
        // finnes med SKE-identifikator. Da blir raden liggende pending i stedet for å sendes mot
        // den avviste originalens referanse.
        test("frigiKravSomVenterPåOpprydding skal beholde avventende status når oppryddingskrav mangler SKE-identifikator") {
            dataSource.transaction { session ->
                session.execute(
                    queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_ENDRING_HOVEDSTOL.value, ENDRING_HOVEDSTOL, "OPPRYDDING-B", 1),
                )
                session.execute(queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_ENDRING_RENTE.value, ENDRING_RENTE, "OPPRYDDING-B", 2))
                session.execute(queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_STOPP.value, STOPP_KRAV, "OPPRYDDING-B", 3))
            }
            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)

            rapportService.frigiKravSomVenterPåOpprydding(1)
            rapportService.frigiKravSomVenterPåOpprydding(2)
            rapportService.frigiKravSomVenterPåOpprydding(3)

            dataSource.transaction { session ->
                val krav = kravRepository.getAllKrav(session)
                krav.first { it.kravId == 1L }.status shouldBe Status.PENDING_ENDRING_HOVEDSTOL
                krav.first { it.kravId == 2L }.status shouldBe Status.PENDING_ENDRING_RENTE
                krav.first { it.kravId == 3L }.status shouldBe Status.PENDING_STOPP
            }
        }

        // Pending-oppfølging kan frigjøres uten nye SFTP-filer. Denne testen beskytter at
        // frigivelsen henter SKE-identifikatoren fra det vellykkede oppryddingskravet før resend.
        test("frigiKravSomVenterPåOpprydding skal kopiere SKE-identifikator før kravet blir sendbart") {
            dataSource.transaction { session ->
                session.execute(
                    queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_ENDRING_HOVEDSTOL.value, ENDRING_HOVEDSTOL, "OPPRYDDING-B", 1),
                )
                session.execute(queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_ENDRING_RENTE.value, ENDRING_RENTE, "OPPRYDDING-B", 2))
                session.execute(queryOf("update krav set status = ?, kravtype = ?, referansenummergammelsak = ? where id = ?", Status.PENDING_STOPP.value, STOPP_KRAV, "OPPRYDDING-B", 3))
                session.execute(
                    queryOf(
                        "update krav set status = ?, kravtype = ?, saksnummer_nav = ?, referansenummergammelsak = ?, kravidentifikator_ske = ? where id = ?",
                        Status.KRAV_SENDT.value,
                        NYTT_KRAV,
                        "OPPRYDDING-B",
                        "AVVIST-A",
                        "ske-oppryddingskrav-b",
                        4,
                    ),
                )
            }
            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)

            rapportService.frigiKravSomVenterPåOpprydding(1)
            rapportService.frigiKravSomVenterPåOpprydding(2)
            rapportService.frigiKravSomVenterPåOpprydding(3)

            dataSource.transaction { session ->
                val krav = kravRepository.getAllKrav(session)
                listOf(1L, 2L, 3L).forEach { id ->
                    krav.first { it.kravId == id }.status shouldBe Status.KRAV_IKKE_SENDT
                    krav.first { it.kravId == id }.kravidentifikatorSKE shouldBe "ske-oppryddingskrav-b"
                }
            }
        }

        test("frigiKravSomVenterPåOpprydding skal følge referansekjeden tilbake til oppryddingskravet") {
            dataSource.transaction { session ->
                session.execute(
                    queryOf(
                        "update krav set status = ?, kravtype = ?, saksnummer_nav = ?, referansenummergammelsak = ?, kravidentifikator_ske = ? where id = ?",
                        Status.KRAV_SENDT.value,
                        NYTT_KRAV,
                        "OPPRYDDING-B",
                        "AVVIST-A",
                        "ske-oppryddingskrav-b",
                        4,
                    ),
                )
                session.execute(
                    queryOf(
                        "update krav set status = ?, kravtype = ?, saksnummer_nav = ?, referansenummergammelsak = ? where id = ?",
                        Status.PENDING_ENDRING_HOVEDSTOL.value,
                        ENDRING_HOVEDSTOL,
                        "OPPFOLGING-C",
                        "OPPRYDDING-B",
                        1,
                    ),
                )
                session.execute(
                    queryOf(
                        "update krav set status = ?, kravtype = ?, saksnummer_nav = ?, referansenummergammelsak = ? where id = ?",
                        Status.PENDING_STOPP.value,
                        STOPP_KRAV,
                        "OPPFOLGING-D",
                        "OPPFOLGING-C",
                        2,
                    ),
                )
            }
            val rapportService = RapportService(dataSource, feilmeldingRepository, kravRepository)

            rapportService.frigiKravSomVenterPåOpprydding(2)

            dataSource.transaction { session ->
                val frigittKrav = kravRepository.getAllKrav(session).first { it.kravId == 2L }
                frigittKrav.status shouldBe Status.KRAV_IKKE_SENDT
                frigittKrav.kravidentifikatorSKE shouldBe "ske-oppryddingskrav-b"
            }
        }
    })
