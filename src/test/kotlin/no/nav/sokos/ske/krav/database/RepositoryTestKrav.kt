package no.nav.sokos.ske.krav.database

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent
import no.nav.sokos.ske.krav.util.getAllKrav

internal class RepositoryTestKrav :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/krav/KravForRepositoryTest.sql")
        }

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            DBListener.dataSource.transaction { tx ->
                KravRepository.getAllKravForStatusCheck(tx).size shouldBe 5
            }
        }

        test(
            "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
        ) {
            val kravForResending = DBListener.dataSource.transaction { tx -> KravRepository.getAllKravForResending(tx) }

            kravForResending.size shouldBe 9
            kravForResending.forEach {
                it.status.shouldBeIn(
                    Status.KRAV_IKKE_SENDT.value,
                    Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND.value,
                    Status.HTTP500_ANNEN_SERVER_FEIL.value,
                    Status.HTTP503_UTILGJENGELIG_TJENESTE.value,
                    Status.HTTP500_INTERN_TJENERFEIL.value,
                )
            }
        }

        test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
            val unsentKrav = DBListener.dataSource.transaction { tx -> KravRepository.getAllUnsentKrav(tx) }
            unsentKrav.size shouldBe 3
            unsentKrav.forEach {
                it.status shouldBe Status.KRAV_IKKE_SENDT.value
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            DBListener.loadInitScript("SQLscript/feilmeldinger/Feilmeldinger.sql")

            DBListener.dataSource.transaction { tx ->
                val kravForAvstemming = KravRepository.getAllKravForAvstemming(tx)
                kravForAvstemming.size shouldBe 4
            }
        }

        test("getSkeKravidentifikator skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            DBListener.dataSource.transaction { tx ->
                KravRepository.getSkeKravidentifikator(tx, "1010-navsaksnummer") shouldBe "1010-skeUUID"
                KravRepository.getSkeKravidentifikator(tx, "1111-navsaksnummer") shouldBe ""
                KravRepository.getSkeKravidentifikator(tx, "1112-navsaksnummer") shouldBe "1112-skeUUID"
                KravRepository.getSkeKravidentifikator(tx, "1113-navsaksnummer") shouldBe "1112-skeUUID"
                KravRepository.getSkeKravidentifikator(tx, "4440-navsaksnummer") shouldBe "4444-skeUUID"
            }
        }

        test("getPreviousReferansenummer skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            DBListener.dataSource.transaction { tx ->
                KravRepository.getPreviousReferansenummer(tx, "2220-navsaksnummer") shouldBe "1110-navsaksnummer"
                KravRepository.getPreviousReferansenummer(tx, "foo-navsaksnummer") shouldBe "foo-navsaksnummer"
            }
        }

        test("getKravTableIdFromCorrelationId skal returnere krav_id basert på corr_id") {
            DBListener.dataSource.transaction { tx ->
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR456") shouldBe 1
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR789") shouldBe 2
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR987") shouldBe 3
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR652") shouldBe 4
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR253") shouldBe 5
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR263482") shouldBe 6
                KravRepository.getKravTableIdFromCorrelationId(tx, "CORR83985902") shouldBe 7
                KravRepository.getKravTableIdFromCorrelationId(tx, "finnesikke") shouldBe 0
            }
        }

        test("updateSentKrav skal oppdatere krav med ny status, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val originalKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR457387" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            DBListener.dataSource.transaction { tx -> KravRepository.updateSentKrav(tx, "CORR457387", "TESTSTATUS") }

            val updatedKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR457387" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val originalKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            DBListener.dataSource.transaction { tx -> KravRepository.updateSentKrav(tx, "CORR83985902", "NykravidentSke", "TESTSTATUS") }

            val updatedKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            val originalKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR457389" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            DBListener.dataSource.transaction { tx -> KravRepository.updateStatus(tx, "NY_STATUS", "CORR457389") }

            val updatedKrav = DBListener.dataSource.getAllKrav().first { it.corrId == "CORR457389" }
            updatedKrav.status shouldBe "NY_STATUS"
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            DBListener.loadInitScript("SQLscript/feilmeldinger/Feilmeldinger.sql")

            val kravForAvstemmingBeforeUpdate = DBListener.dataSource.transaction { tx -> KravRepository.getAllKravForAvstemming(tx) }
            val firstKrav = kravForAvstemmingBeforeUpdate.first()
            val lastKrav = kravForAvstemmingBeforeUpdate.last()

            DBListener.dataSource.transaction { tx -> KravRepository.updateStatusForAvstemtKravToReported(tx, firstKrav.kravId.toInt()) }
            DBListener.dataSource.transaction { tx -> KravRepository.updateStatusForAvstemtKravToReported(tx, lastKrav.kravId.toInt()) }

            val kravForAvstemmingAfterUpdate = DBListener.dataSource.transaction { tx -> KravRepository.getAllKravForAvstemming(tx) }
            kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2

            DBListener.dataSource.transaction { tx ->
                val feilmelding1 = FeilmeldingRepository.getFeilmeldingForKravId(tx, firstKrav.kravId)
                val feilmelding2 = FeilmeldingRepository.getFeilmeldingForKravId(tx, lastKrav.kravId)

                feilmelding1.first().rapporter shouldBe false
                feilmelding2.first().rapporter shouldBe false
            }
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            val originalNyttKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
            originalNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

            DBListener.dataSource.transaction { tx ->
                KravRepository.updateEndringWithSkeKravIdentifikator(tx, "7770-navsaksnummer", "Ny_ske_saksnummer")
            }
            val updatedNyttKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
            updatedNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

            val originalStoppKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            originalStoppKrav.kravidentifikatorSKE shouldBe "3333-skeUUID"

            DBListener.dataSource.transaction { tx ->
                KravRepository.updateEndringWithSkeKravIdentifikator(tx, "3330-navsaksnummer", "Ny_ske_saksnummer")
            }
            val updatedStoppKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedStoppKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"

            val originalEndreKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
            originalEndreKrav.kravidentifikatorSKE shouldBe "1111-skeUUID"

            DBListener.dataSource.transaction { tx ->
                KravRepository.updateEndringWithSkeKravIdentifikator(tx, "2220-navsaksnummer", "Ny_ske_saksnummer")
            }
            val updatedEndreKrav = DBListener.dataSource.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedEndreKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"
        }

        test("insertAllNewKrav skal inserte alle kravlinjene") {
            val filnavn = "TiNyeKrav1Endring1Stopp.txt"
            val liste = getFileContent("krav/$filnavn")
            val kravlinjer = FileParser(liste).parseKravLinjer()

            val kravBefore = DBListener.dataSource.getAllKrav()
            DBListener.dataSource.transaction { tx -> KravRepository.insertAllNewKrav(tx, kravlinjer, filnavn) }
            val lagredeKrav = DBListener.dataSource.getAllKrav()

            lagredeKrav.size shouldBe kravlinjer.size + kravBefore.size + 1
            lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8 + kravBefore.filter { it.kravtype == NYTT_KRAV }.size
            lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1 + kravBefore.filter { it.kravtype == STOPP_KRAV }.size
            lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_RENTE }.size
            lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_HOVEDSTOL }.size
        }

        test("deleteOldKrav skal slette alle kravene som ble opprettet før en spesifisert tid") {
            val threshold = LocalDate.parse("2023-01-02")
            val kravDeleted = DBListener.dataSource.transaction { tx -> KravRepository.deleteOldKrav(tx, threshold) }
            kravDeleted shouldBe 17
        }

        afterTest {
            DBListener.clearDB()
        }
    })
