package no.nav.sokos.ske.krav.database

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.getFeilmeldingForKravId
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForAvstemming
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForResending
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllKravForStatusCheck
import no.nav.sokos.ske.krav.database.repository.KravRepository.getAllUnsentKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.getKravTableIdFromCorrelationId
import no.nav.sokos.ske.krav.database.repository.KravRepository.getPreviousReferansenummer
import no.nav.sokos.ske.krav.database.repository.KravRepository.getSkeKravidentifikator
import no.nav.sokos.ske.krav.database.repository.KravRepository.insertAllNewKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateEndringWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateSentKrav
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.database.repository.KravRepository.updateStatusForAvstemtKravToReported
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.nav.FileParser
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent
import no.nav.sokos.ske.krav.util.TestContainer
import no.nav.sokos.ske.krav.util.getAllKrav

internal class RepositoryTestKrav :
    FunSpec({

        val testContainer = TestContainer()
        testContainer.loadInitScript("SQLscript/KravForRepositoryBehaviourTestScript.sql")
        testContainer.loadInitScript("SQLscript/Feilmeldinger.sql")

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            testContainer.dataSource.connection.use { it.getAllKravForStatusCheck().size shouldBe 5 }
        }
        test(
            "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
        ) {
            val kravForResending = testContainer.dataSource.connection.use { it.getAllKravForResending() }

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
            val unsentKrav = testContainer.dataSource.connection.use { it.getAllUnsentKrav() }
            unsentKrav.size shouldBe 3
            unsentKrav.forEach {
                it.status shouldBe Status.KRAV_IKKE_SENDT.value
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            testContainer.dataSource.connection.use {
                val kravForAvstemming = it.getAllKravForAvstemming()
                kravForAvstemming.size shouldBe 4
            }
        }

        test("getSkeKravidentifikator skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            testContainer.dataSource.connection.use {
                it.getSkeKravidentifikator("1010-navsaksnummer") shouldBe "1010-skeUUID"
                it.getSkeKravidentifikator("1111-navsaksnummer") shouldBe ""
                it.getSkeKravidentifikator("1112-navsaksnummer") shouldBe "1112-skeUUID"
                it.getSkeKravidentifikator("1113-navsaksnummer") shouldBe "1112-skeUUID"
                it.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID"
            }
        }
        test("getPreviousReferansenummer skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            testContainer.dataSource.connection.use {
                it.getPreviousReferansenummer("2220-navsaksnummer") shouldBe "1110-navsaksnummer"
                it.getPreviousReferansenummer("foo-navsaksnummer") shouldBe "foo-navsaksnummer"
            }
        }

        test("getKravTableIdFromCorrelationId skal returnere krav_id basert på corr_id") {
            testContainer.dataSource.connection.use {
                it.getKravTableIdFromCorrelationId("CORR456") shouldBe 1
                it.getKravTableIdFromCorrelationId("CORR789") shouldBe 2
                it.getKravTableIdFromCorrelationId("CORR987") shouldBe 3
                it.getKravTableIdFromCorrelationId("CORR652") shouldBe 4
                it.getKravTableIdFromCorrelationId("CORR253") shouldBe 5
                it.getKravTableIdFromCorrelationId("CORR263482") shouldBe 6
                it.getKravTableIdFromCorrelationId("CORR83985902") shouldBe 7
                it.getKravTableIdFromCorrelationId("finnesikke") shouldBe 0
            }
        }

        test("updateSentKrav skal oppdatere krav med ny status, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            testContainer.dataSource.connection.use { con ->
                val originalKrav = con.getAllKrav().first { it.corrId == "CORR457387" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                con.updateSentKrav("CORR457387", "TESTSTATUS")

                val updatedKrav = con.getAllKrav().first { it.corrId == "CORR457387" }
                updatedKrav.status shouldBe "TESTSTATUS"
                updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            testContainer.dataSource.connection.use { con ->
                val originalKrav = con.getAllKrav().first { it.corrId == "CORR83985902" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
                originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                con.updateSentKrav("CORR83985902", "NykravidentSke", "TESTSTATUS")

                val updatedKrav = con.getAllKrav().first { it.corrId == "CORR83985902" }
                updatedKrav.status shouldBe "TESTSTATUS"
                updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
                updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            testContainer.dataSource.connection.use { con ->
                val originalKrav = con.getAllKrav().first { it.corrId == "CORR457389" }
                originalKrav.status shouldBe "RESKONTROFOERT"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                con.updateStatus("NY_STATUS", "CORR457389")

                val updatedKrav = con.getAllKrav().first { it.corrId == "CORR457389" }
                updatedKrav.status shouldBe "NY_STATUS"
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            val kravForAvstemmingBeforeUpdate = testContainer.dataSource.connection.use { it.getAllKravForAvstemming() }
            val firstKrav = kravForAvstemmingBeforeUpdate.first()
            val lastKrav = kravForAvstemmingBeforeUpdate.last()

            testContainer.dataSource.connection.use {
                it.updateStatusForAvstemtKravToReported(firstKrav.kravId.toInt())
            }
            testContainer.dataSource.connection.use {
                it.updateStatusForAvstemtKravToReported(lastKrav.kravId.toInt())
            }

            val kravForAvstemmingAfterUpdate = testContainer.dataSource.connection.use { it.getAllKravForAvstemming() }
            kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2

            val feilmelding1 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(firstKrav.kravId) }
            val feilmelding2 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(lastKrav.kravId) }

            feilmelding1.first().rapporter shouldBe false
            feilmelding2.first().rapporter shouldBe false
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            testContainer.dataSource.connection.use { con ->
                val originalNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
                originalNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"

                con.updateEndringWithSkeKravIdentifikator("7770-navsaksnummer", "Ny_ske_saksnummer")

                val updatedNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "7770-navsaksnummer" }
                updatedNyttKrav.kravidentifikatorSKE shouldBe "7777-skeUUID"
            }

            testContainer.dataSource.connection.use { con ->
                val originalStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
                originalStoppKrav.kravidentifikatorSKE shouldBe "3333-skeUUID"

                con.updateEndringWithSkeKravIdentifikator("3330-navsaksnummer", "Ny_ske_saksnummer")

                val updatedStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
                updatedStoppKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"
            }

            testContainer.dataSource.connection.use { con ->
                val originalEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
                originalEndreKrav.kravidentifikatorSKE shouldBe "1111-skeUUID"

                con.updateEndringWithSkeKravIdentifikator("2220-navsaksnummer", "Ny_ske_saksnummer")

                val updatedEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
                updatedEndreKrav.kravidentifikatorSKE shouldBe "Ny_ske_saksnummer"
            }
        }

        test("insertAllNewKrav skal inserte alle kravlinjene") {
            val filnavn = "8NyeKrav1Endring1Stopp.txt"
            val liste = getFileContent(filnavn)
            val kravlinjer = FileParser(liste).parseKravLinjer()
            val kravBefore = testContainer.dataSource.connection.getAllKrav()

            testContainer.dataSource.connection.use { con ->

                con.insertAllNewKrav(kravlinjer, filnavn)
                val lagredeKrav = con.getAllKrav()
                lagredeKrav.size shouldBe kravlinjer.size + kravBefore.size + 1
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8 + kravBefore.filter { it.kravtype == NYTT_KRAV }.size
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1 + kravBefore.filter { it.kravtype == STOPP_KRAV }.size
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_RENTE }.size
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1 + kravBefore.filter { it.kravtype == ENDRING_HOVEDSTOL }.size
            }
        }
    })
