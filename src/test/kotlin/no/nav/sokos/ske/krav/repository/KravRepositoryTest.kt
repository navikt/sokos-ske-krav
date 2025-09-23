package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotliquery.sessionOf
import kotliquery.using

import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.dataSource
import no.nav.sokos.ske.krav.util.FileParser
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.util.TestUtilFunctions

internal class KravRepositoryTest :
    FunSpec({
        extensions(PostgresListener)

        PostgresListener.migrate("SQLscript/KravForRepositoryBehaviourTestScript.sql")
        PostgresListener.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                KravRepository.getAllKravForStatusCheck(session).size shouldBe 5
            }
        }
        test("getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                val kravForResending = KravRepository.getAllKravForResending(session)

                kravForResending.size shouldBe 9
                kravForResending.forEach {
                    it.status.shouldBeIn(
                        Status.KRAV_IKKE_SENDT.value,
                        Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value,
                        Status.HTTP500_ANNEN_SERVER_FEIL.value,
                        Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                        Status.HTTP500_INTERN_TJENERFEIL.value,
                    )
                }
            }
        }
        test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                val unsentKrav = KravRepository.getAllUnsentKrav(session)
                unsentKrav.size shouldBe 3
                unsentKrav.forEach {
                    it.status shouldBe Status.KRAV_IKKE_SENDT.value
                }
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                val kravForAvstemming = KravRepository.getAllKravForAvstemming(session)
                kravForAvstemming.size shouldBe 4
            }
        }

        test("getSkeKravidentifikator skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                KravRepository.getSkeKravidentifikator(session, "1010-navsaksnummer") shouldBe "1010-skeUUID"
                KravRepository.getSkeKravidentifikator(session, "1111-navsaksnummer") shouldBe ""
                KravRepository.getSkeKravidentifikator(session, "1112-navsaksnummer") shouldBe "1112-skeUUID"
                KravRepository.getSkeKravidentifikator(session, "1113-navsaksnummer") shouldBe "1112-skeUUID"
                KravRepository.getSkeKravidentifikator(session, "4440-navsaksnummer") shouldBe "4444-skeUUID"
            }
        }

        test("getPreviousReferansenummer skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                KravRepository.getPreviousReferansenummer(session, "2220-navsaksnummer") shouldBe "1110-navsaksnummer"
                KravRepository.getPreviousReferansenummer(session, "foo-navsaksnummer") shouldBe "foo-navsaksnummer"
            }
        }

        test("getKravTableIdFromCorrelationId skal returnere krav_id basert på corr_id") {
            using(sessionOf(PostgresListener.dataSource)) { session ->
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR456") shouldBe 1
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR789") shouldBe 2
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR987") shouldBe 3
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR652") shouldBe 4
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR253") shouldBe 5
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR263482") shouldBe 6
                KravRepository.getKravTableIdFromCorrelationId(session, "CORR83985902") shouldBe 7
                KravRepository.getKravTableIdFromCorrelationId(session, "finnesikke") shouldBe 0
            }
        }

        test("updateSentKrav skal oppdatere krav med ny status, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            dataSource.transaction { session ->
                val originalKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR457387" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                KravRepository.updateSentKravStatus(session, "CORR457387", "TESTSTATUS")

                val updatedKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR457387" }
                updatedKrav.status shouldBe "TESTSTATUS"
                updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            dataSource.transaction { session ->
                val originalKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR83985902" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
                originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                KravRepository.updateSentKravStatusMedKravIdentifikator(session, "CORR83985902", "NykravidentSke", "TESTSTATUS")

                val updatedKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR83985902" }
                updatedKrav.status shouldBe "TESTSTATUS"
                updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
                updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            dataSource.transaction { session ->
                val originalKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR457389" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                KravRepository.updateStatus(session, "NY_STATUS", "CORR457389")

                val updatedKrav = KravRepository.getAllKrav(session).first { it.corrId == "CORR457389" }
                updatedKrav.status shouldBe "NY_STATUS"
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            dataSource.transaction { session ->
                val kravForAvstemmingBeforeUpdate = KravRepository.getAllKravForAvstemming(session)
                val firstKrav = kravForAvstemmingBeforeUpdate.first()
                val lastKrav = kravForAvstemmingBeforeUpdate.last()

                KravRepository.updateStatusForAvstemtKravToReported(session, firstKrav.kravId.toInt())
                KravRepository.updateStatusForAvstemtKravToReported(session, lastKrav.kravId.toInt())

                val kravForAvstemmingAfterUpdate = KravRepository.getAllKravForAvstemming(session)
                kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2

                val feilmelding1 = FeilmeldingRepository.getFeilmeldingForKravId(session, firstKrav.kravId)
                val feilmelding2 = FeilmeldingRepository.getFeilmeldingForKravId(session, lastKrav.kravId)

                feilmelding1.first().rapporter shouldBe false
                feilmelding2.first().rapporter shouldBe false
            }
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            dataSource.transaction { session ->
                val originalNyttKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "7770-navsaksnummer" }
                originalNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

                KravRepository.updateEndringWithSkeKravIdentifikator(session, "7770-navsaksnummer", "Ny_ske_saksnummer")

                val updatedNyttKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "7770-navsaksnummer" }
                updatedNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

                val originalStoppKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "3330-navsaksnummer" }
                originalStoppKrav.kravidentifikatorSKE shouldBe "3333-skeUUID"

                KravRepository.updateEndringWithSkeKravIdentifikator(session, "3330-navsaksnummer", "Ny_ske_saksnummer")

                val updatedStoppKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "3330-navsaksnummer" }
                updatedStoppKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"

                val originalEndreKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "2220-navsaksnummer" }
                originalEndreKrav.kravidentifikatorSKE shouldBe "1111-skeUUID"

                KravRepository.updateEndringWithSkeKravIdentifikator(session, "2220-navsaksnummer", "Ny_ske_saksnummer")

                val updatedEndreKrav = KravRepository.getAllKrav(session).first { it.saksnummerNAV == "3330-navsaksnummer" }
                updatedEndreKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"
            }
        }

        test("insertAllNewKrav skal inserte alle kravlinjene og legg til 1 ekstra rad for ENDRING_HOVEDSTOL") {
            val filnavn = "8NyeKrav1Endring1Stopp.txt"
            val liste = TestUtilFunctions.getFileContent(filnavn)
            val kravlinjer = FileParser.parseKravLinjer(liste)

            dataSource.transaction { session ->
                val kravBefore = KravRepository.getAllKrav(session)
                KravRepository.insertAllNewKrav(session, kravlinjer, filnavn)

                val lagredeKrav = KravRepository.getAllKrav(session)
                lagredeKrav.size shouldBe kravlinjer.size + kravBefore.size + 1
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8 + kravBefore.filter { it.kravtype == NYTT_KRAV }.size
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1 + kravBefore.filter { it.kravtype == STOPP_KRAV }.size
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_RENTE }.size
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_HOVEDSTOL }.size
            }
        }
    })
