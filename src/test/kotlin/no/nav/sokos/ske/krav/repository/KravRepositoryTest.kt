package no.nav.sokos.ske.krav.repository

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.dataSource
import no.nav.sokos.ske.krav.listener.PostgresListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.PostgresListener.kravRepository
import no.nav.sokos.ske.krav.util.FileParser
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.util.TestUtilFunctions

internal class KravRepositoryTest :
    FunSpec({
        extensions(PostgresListener)

        PostgresListener.migrate("SQLscript/KravForRepositoryBehaviourTestScript.sql")
        PostgresListener.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            kravRepository.getAllKravForStatusCheck().size shouldBe 5
        }
        test("getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ") {
            val kravForResending = kravRepository.getAllKravForResending()

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
        test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
            val unsentKrav = kravRepository.getAllUnsentKrav()
            unsentKrav.size shouldBe 3
            unsentKrav.forEach {
                it.status shouldBe Status.KRAV_IKKE_SENDT.value
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            val kravForAvstemming = kravRepository.getAllKravForAvstemming()
            kravForAvstemming.size shouldBe 4
        }

        test("getSkeKravidentifikator skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            kravRepository.getSkeKravidentifikator("1010-navsaksnummer") shouldBe "1010-skeUUID"
            kravRepository.getSkeKravidentifikator("1111-navsaksnummer") shouldBe ""
            kravRepository.getSkeKravidentifikator("1112-navsaksnummer") shouldBe "1112-skeUUID"
            kravRepository.getSkeKravidentifikator("1113-navsaksnummer") shouldBe "1112-skeUUID"
            kravRepository.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID"
        }

        test("getPreviousReferansenummer skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            kravRepository.getPreviousReferansenummer("2220-navsaksnummer") shouldBe "1110-navsaksnummer"
            kravRepository.getPreviousReferansenummer("foo-navsaksnummer") shouldBe "foo-navsaksnummer"
        }

        test("getKravTableIdFromCorrelationId skal returnere krav_id basert på corr_id") {
            kravRepository.getKravTableIdFromCorrelationId("CORR456") shouldBe 1
            kravRepository.getKravTableIdFromCorrelationId("CORR789") shouldBe 2
            kravRepository.getKravTableIdFromCorrelationId("CORR987") shouldBe 3
            kravRepository.getKravTableIdFromCorrelationId("CORR652") shouldBe 4
            kravRepository.getKravTableIdFromCorrelationId("CORR253") shouldBe 5
            kravRepository.getKravTableIdFromCorrelationId("CORR263482") shouldBe 6
            kravRepository.getKravTableIdFromCorrelationId("CORR83985902") shouldBe 7
            kravRepository.getKravTableIdFromCorrelationId("finnesikke") shouldBe 0
        }

        test("updateSentKrav skal oppdatere krav med ny status, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {

            val originalKrav = kravRepository.getAllKrav().first { it.corrId == "CORR457387" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            dataSource.transaction { session ->
                kravRepository.updateSentKravStatus("CORR457387", "TESTSTATUS", session)
            }

            val updatedKrav = kravRepository.getAllKrav().first { it.corrId == "CORR457387" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val originalKrav = kravRepository.getAllKrav().first { it.corrId == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            dataSource.transaction { session ->
                kravRepository.updateSentKravStatusMedKravIdentifikator("CORR83985902", "NykravidentSke", "TESTSTATUS", session)
            }

            val updatedKrav = kravRepository.getAllKrav().first { it.corrId == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            val originalKrav = kravRepository.getAllKrav().first { it.corrId == "CORR457389" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            dataSource.transaction { session ->
                kravRepository.updateStatus("NY_STATUS", "CORR457389", session)
            }

            val updatedKrav = kravRepository.getAllKrav().first { it.corrId == "CORR457389" }
            updatedKrav.status shouldBe "NY_STATUS"
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            val kravForAvstemmingBeforeUpdate = kravRepository.getAllKravForAvstemming()
            val firstKrav = kravForAvstemmingBeforeUpdate.first()
            val lastKrav = kravForAvstemmingBeforeUpdate.last()

            dataSource.transaction { session ->
                kravRepository.updateStatusForAvstemtKravToReported(firstKrav.kravId.toInt(), session)
                kravRepository.updateStatusForAvstemtKravToReported(lastKrav.kravId.toInt(), session)
            }

            val kravForAvstemmingAfterUpdate = kravRepository.getAllKravForAvstemming()
            kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2

            val feilmelding1 = feilmeldingRepository.getFeilmeldingForKravId(firstKrav.kravId)
            val feilmelding2 = feilmeldingRepository.getFeilmeldingForKravId(lastKrav.kravId)

            feilmelding1.first().rapporter shouldBe false
            feilmelding2.first().rapporter shouldBe false
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            val originalNyttKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
            originalNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

            dataSource.transaction { session ->
                kravRepository.updateEndringWithSkeKravIdentifikator("7770-navsaksnummer", "Ny_ske_saksnummer", session)
            }

            val updatedNyttKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
            updatedNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

            val originalStoppKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            originalStoppKrav.kravidentifikatorSKE shouldBe "3333-skeUUID"

            dataSource.transaction { session ->
                kravRepository.updateEndringWithSkeKravIdentifikator("3330-navsaksnummer", "Ny_ske_saksnummer", session)
            }

            val updatedStoppKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedStoppKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"

            val originalEndreKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
            originalEndreKrav.kravidentifikatorSKE shouldBe "1111-skeUUID"

            dataSource.transaction { session ->
                kravRepository.updateEndringWithSkeKravIdentifikator("2220-navsaksnummer", "Ny_ske_saksnummer", session)
            }

            val updatedEndreKrav = kravRepository.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedEndreKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"
        }

        test("insertAllNewKrav skal inserte alle kravlinjene og legg til 1 ekstra rad for ENDRING_HOVEDSTOL") {
            val filnavn = "8NyeKrav1Endring1Stopp.txt"
            val liste = TestUtilFunctions.getFileContent(filnavn)
            val kravlinjer = FileParser.parseKravLinjer(liste)
            val kravBefore = kravRepository.getAllKrav()

            dataSource.transaction { session ->
                kravRepository.insertAllNewKrav(kravlinjer, filnavn, session)
            }

            val lagredeKrav = kravRepository.getAllKrav()
            lagredeKrav.size shouldBe kravlinjer.size + kravBefore.size + 1
            lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8 + kravBefore.filter { it.kravtype == NYTT_KRAV }.size
            lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1 + kravBefore.filter { it.kravtype == STOPP_KRAV }.size
            lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_RENTE }.size
            lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_HOVEDSTOL }.size
        }
    })
