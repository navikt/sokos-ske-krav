package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import sokos.ske.krav.database.Repository.getAllErrorMessages
import sokos.ske.krav.database.Repository.getAllKravForAvstemming
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllUnsentKrav
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getErrorMessageForKravId
import sokos.ske.krav.database.Repository.getKravTableIdFromCorrelationId
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertErrorMessage
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.Repository.updateSentKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.Status

import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV

import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.readFileFromFS
import sokos.ske.krav.util.startContainer
import java.time.LocalDate
import java.time.LocalDateTime


internal class RepositoryTest : FunSpec({


    test("getAllKravForAvstemming skal returnere alle krav som ikke har status RESKONTROFOERT eller VALIDERINGFEIL_RAPPORTERT") {
        val dataSource = startContainer(this.testCase.name.testName, listOf("KravSomSkalAvstemmes.sql"))
        val kravForAvstemming = dataSource.connection.getAllKravForAvstemming()
        kravForAvstemming.size shouldBe 9
    }

    test("updateStatusForAvstemtKravToReported skal sette status til VALIDERINGFEIL_RAPPORTERT på krav med angitt kravid") {
        val dataSource = startContainer(this.testCase.name.testName, listOf("KravSomSkalAvstemmes.sql"))
        val kravForAvstemmingBeforeUpdate = dataSource.connection.getAllKravForAvstemming()

        val firstKrav = kravForAvstemmingBeforeUpdate.first()
        val lastKrav = kravForAvstemmingBeforeUpdate.last()
        firstKrav.status shouldNotBe Status.VALIDERINGFEIL_RAPPORTERT.value
        lastKrav.status shouldNotBe Status.VALIDERINGFEIL_RAPPORTERT.value

        dataSource.connection.use { ds ->
            ds.updateStatusForAvstemtKravToReported(firstKrav.kravId.toInt())
            ds.updateStatusForAvstemtKravToReported(lastKrav.kravId.toInt())
        }

        val kravForAvstemmingAfterUpdate = dataSource.connection.getAllKravForAvstemming()
        kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2
        kravForAvstemmingAfterUpdate.filter { it.status == Status.VALIDERINGFEIL_RAPPORTERT.value }.size shouldBe 0

        val alleKrav = dataSource.connection.getAllKrav()
        val firstKravAfterUpdate = alleKrav.find { it.kravId == firstKrav.kravId }
        val lastKravAfterUpdate = alleKrav.find { it.kravId == lastKrav.kravId }

        firstKravAfterUpdate?.status shouldBe Status.VALIDERINGFEIL_RAPPORTERT.value
        lastKravAfterUpdate?.status shouldBe Status.VALIDERINGFEIL_RAPPORTERT.value

    }


    test("getErrorMessageForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
        startContainer(this.testCase.name.testName, listOf("Feilmeldinger.sql")).use { ds ->
            val feilmelding1 = ds.connection.getErrorMessageForKravId(1)
            feilmelding1.size shouldBe 1
            feilmelding1.first().corrId shouldBe "CORR856"
            val feilmelding2 = ds.connection.getErrorMessageForKravId(2)
            feilmelding2.size shouldBe 2
            feilmelding2.filter { it.error == "404" }.size shouldBe 1
            feilmelding2.filter { it.error == "422" }.size shouldBe 1
        }
    }

    test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllKravForStatusCheck().size shouldBe 2
        }
    }
    test("getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllKravForResending().size shouldBe 7
        }
    }
    test("getAllKravNotSent skal returnere krav som har status KRAV_IKKE_SENDT") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllUnsentKrav().size shouldBe 1
        }
    }
    test("getAllValidationErrors skal returnere krav som har status VALIDERINGSFEIL_422") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllValidationErrors().size shouldBe 1
        }
    }

    test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
        startContainer(this.testCase.name.testName, listOf("Feilmeldinger.sql")).use { ds ->
            ds.connection.getAllErrorMessages().size shouldBe 3
        }
    }

    test("getSkeKravIdent skal returnere kravidentifikator_ske basert på saksnummer_nav hvor kravtype er NYTT_KRAV") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getSkeKravidentifikator("2220-navsaksnummer") shouldBe ""
            ds.connection.getSkeKravidentifikator("3330-navsaksnummer") shouldBe ""
            ds.connection.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID"
        }
    }

    test("getKravIdfromCorrId skal returnere krav_id basert på corr_id") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getKravTableIdFromCorrelationId("CORR456") shouldBe 1
            ds.connection.getKravTableIdFromCorrelationId("CORR789") shouldBe 2
            ds.connection.getKravTableIdFromCorrelationId("CORR987") shouldBe 3
            ds.connection.getKravTableIdFromCorrelationId("CORR652") shouldBe 4
            ds.connection.getKravTableIdFromCorrelationId("CORR253") shouldBe 5
            ds.connection.getKravTableIdFromCorrelationId("CORR263482") shouldBe 6
            ds.connection.getKravTableIdFromCorrelationId("CORR83985902") shouldBe 7
            ds.connection.getKravTableIdFromCorrelationId("finnesikke") shouldBe 0
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSentKrav("CORR83985902", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.saksnummerSKE shouldBe "6666-skeUUID"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSentKrav("CORR83985902", "NykravidentSke", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.saksnummerSKE shouldBe "NykravidentSke"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og ny corr_id, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSentKrav("NYCORRID", "CORR83985902", "NYTT_KRAV", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "NYCORRID" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateStatus("NY_STATUS", "CORR83985902")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "NY_STATUS"
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    //endring for hovedstol er ikke med i test?
    test("insertAllNewKrav skal inserte alle kravlinjene") {
        val liste = readFileFromFS("8NyeKrav1Endring1Stopp.txt".asResource())
        val kravlinjer = FileParser(liste).parseKravLinjer()
        startContainer(this.testCase.name.testName, emptyList()).use { ds ->
            ds.connection.insertAllNewKrav(kravlinjer)
            val lagredeKrav = ds.connection.getAllKrav()
            lagredeKrav.size shouldBe kravlinjer.size + 1
            lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8
            lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1
            lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1
            lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1
        }
    }

    test("setSkeKravIdentPaEndring skal sette kravidentifikator_ske med gitt saksnummer hvis kravet ikke er et nytt krav") {
        val ds = startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql"))

        ds.connection.use { con ->
            val originalNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "6660-navsaksnummer" }
            originalNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("6660-navsaksnummer", "Ny_ske_saksnummer")

            val updatedNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "6660-navsaksnummer" }
            updatedNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"
        }

        ds.connection.use { con ->
            val originalStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            originalStoppKrav.saksnummerSKE shouldBe "3333-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("3330-navsaksnummer", "Ny_ske_saksnummer")

            val updatedStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedStoppKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
        }

        ds.connection.use { con ->
            val originalEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
            originalEndreKrav.saksnummerSKE shouldBe "2222-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("2220-navsaksnummer", "Ny_ske_saksnummer")

            val updatedEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedEndreKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
        }
    }


    test("saveErrorMessage skal lagre feilmelding") {
        val feilmelding = FeilmeldingTable(
            2L, 1L, "CORR456", "1110-navsaksnummer", "1111-skeUUID",
            "409", "feilmelding 409 1111", "{nav request2}", "{ske response 2}", LocalDateTime.now()
        )

        startContainer(this.testCase.name.testName, listOf("Feilmeldinger.sql")).use { ds ->
            ds.connection.use { con ->
                con.getAllErrorMessages().size shouldBe 3
                con.insertErrorMessage(feilmelding)

                val feilmeldinger = con.getAllErrorMessages()
                feilmeldinger.size shouldBe 4
                feilmeldinger.filter { it.kravId == 1L }.size shouldBe 2
                feilmeldinger.filter { it.corrId == "CORR456" }.size shouldBe 1
                feilmeldinger.filter { it.corrId == "CORR856" }.size shouldBe 1
                feilmeldinger.filter { it.corrId == "CORR658" }.size shouldBe 2
            }
        }
    }
})

