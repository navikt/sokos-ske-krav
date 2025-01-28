package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.Repository.getAllFeilmeldinger
import sokos.ske.krav.database.Repository.getAllKravForAvstemming
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllUnsentKrav
import sokos.ske.krav.database.Repository.getFeilmeldingForKravId
import sokos.ske.krav.database.Repository.getKravTableIdFromCorrelationId
import sokos.ske.krav.database.Repository.getPreviousReferansenummer
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.insertFeilmelding
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.Repository.updateSentKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.getAllKrav
import java.time.LocalDate
import java.time.LocalDateTime

// TODO: sorter i get-update-insert, eller etter domeneobjekt
internal class RepositoryBehaviourTest :
    FunSpec({
        val testContainer = TestContainer()
        testContainer.migrate("SQLscript/KravForRepositoryBehaviourTestScript.sql")
        testContainer.migrate("SQLscript/Feilmeldinger.sql")
        testContainer.migrate("SQLscript/ValideringsFeil.sql")

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

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummer") {
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

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            testContainer.dataSource.connection.use { it.getAllKravForStatusCheck().size shouldBe 5 }
        }
        test(
            "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
        ) {
            val kravForResending = testContainer.dataSource.connection.use { it.getAllKravForResending() }

            kravForResending.size shouldBe 9
            kravForResending.forEach {
                it.status.shouldBeIn(Status.KRAV_IKKE_SENDT.value, Status.HTTP409_IKKE_RESKONTROFORT_RESEND.value, Status.HTTP500_ANNEN_SERVER_FEIL.value, Status.HTTP503_UTILGJENGELIG_TJENESTE.value, Status.HTTP500_INTERN_TJENERFEIL.value)
            }
        }
        test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
            val unsentKrav = testContainer.dataSource.connection.use { it.getAllUnsentKrav() }
            unsentKrav.size shouldBe 3
            unsentKrav.forEach {
                it.status shouldBe Status.KRAV_IKKE_SENDT.value
            }
        }
        test("getSkeKravIdent skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            testContainer.dataSource.connection.use {
                it.getSkeKravidentifikator("1010-navsaksnummer") shouldBe "1010-skeUUID"
                it.getSkeKravidentifikator("1111-navsaksnummer") shouldBe ""
                it.getSkeKravidentifikator("1112-navsaksnummer") shouldBe "1112-skeUUID"
                it.getSkeKravidentifikator("1113-navsaksnummer") shouldBe "1112-skeUUID"
                it.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID"
            }
        }
        test("getPreviousOldRef skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            testContainer.dataSource.connection.use {
                it.getPreviousReferansenummer("2220-navsaksnummer") shouldBe "1110-navsaksnummer"
                it.getPreviousReferansenummer("foo-navsaksnummer") shouldBe "foo-navsaksnummer"
            }
        }

        test("getKravIdfromCorrId skal returnere krav_id basert på corr_id") {
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

        // TODO: Valideringsfeil
        test("getValideringsFeilForLinje skal returnere valideringsfeil basert på filnavn og linjenummer") {}
        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {}
        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {}
        test("insertLineValideringsfeil skal inserte ny valideringsfeil med filnanvn, linjenummer, saksnummerNav, kravlinje, og feilmelding") {}

        test("getAllErrorMessages skal returnere alle feilmeldinger ") {
            testContainer.dataSource.connection.use { it.getAllFeilmeldinger().size shouldBe 4 }
        }

        test("getErrorMessageForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            testContainer.dataSource.connection.use { con ->
                val feilmelding1 = con.getFeilmeldingForKravId(1)
                feilmelding1.size shouldBe 1
                feilmelding1.first().corrId shouldBe "CORR856"

                val feilmelding2 = con.getFeilmeldingForKravId(2)
                feilmelding2.size shouldBe 2
                feilmelding2.filter { it.error == "404" }.size shouldBe 2
                feilmelding2.map { it.corrId shouldBe "CORR658" }

                val feilmelding3 = con.getFeilmeldingForKravId(3)
                feilmelding3.size shouldBe 1
                feilmelding3.filter { it.error == "500" }.size shouldBe 1
                feilmelding3.first().corrId shouldBe "CORR457389"
            }
        }
        test("insertErrorMessage skal lagre feilmelding") {
            val feilmelding =
                FeilmeldingTable(
                    2L,
                    999L,
                    "CORR456",
                    "1110-navsaksnummer",
                    "1111-skeUUID",
                    "409",
                    "feilmelding 409 1111",
                    "{nav request2}",
                    "{ske response 2}",
                    LocalDateTime.now(),
                    false,
                )

            testContainer.dataSource.connection.use { con ->
                con.getAllFeilmeldinger().size shouldBe 4
                con.insertFeilmelding(feilmelding)

                val feilmeldinger = con.getAllFeilmeldinger()
                feilmeldinger.size shouldBe 5
                feilmeldinger.filter { it.corrId == feilmelding.corrId }.size shouldBe 1
                feilmeldinger.filter { it.corrId == "CORR856" }.size shouldBe 1
                feilmeldinger.filter { it.corrId == "CORR658" }.size shouldBe 2
                feilmeldinger.filter { it.corrId == "CORR457389" }.size shouldBe 1
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            testContainer.dataSource.connection.use {
                val kravForAvstemming = it.getAllKravForAvstemming()
                kravForAvstemming.size shouldBe 4
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
    })
