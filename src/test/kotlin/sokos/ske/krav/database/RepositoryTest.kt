package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.Repository.getAllKravForResending
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getAllKravNotSent
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getKravIdfromCorrId
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.setSkeKravIdentPaEndring
import sokos.ske.krav.database.Repository.updateSendtKrav
import sokos.ske.krav.database.Repository.updateStatus

import sokos.ske.krav.domain.nav.FileParser

import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.readFileFromFS
import sokos.ske.krav.util.startContainer
import java.time.LocalDate


internal class RepositoryTest : FunSpec({

    test("getAllKravForStatusCheck skal returnere krav som ikke har status RESKONTROFOERT eller VALIDERINGSFEIL_422 eller KRAV_IKKE_SENDT"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllKravForStatusCheck().size shouldBe 4
        }
    }
    test("getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT eller IKKE_RESKONTROFORT_RESEND"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllKravForResending().size shouldBe 4
        }
    }
    test("getAllKravNotSent skal returnere krav som har status KRAV_IKKE_SENDT"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllKravNotSent().size shouldBe 1
        }
    }
    test("getAllValidationErrors skal returnere krav som har status VALIDERINGSFEIL_422"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getAllValidationErrors().size shouldBe 1
        }
    }

    test("getSkeKravIdent skal returnere kravidentifikator_ske basert på saksnummer_nav hvor kravtype er NYTT_KRAV"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getSkeKravIdent("2220-navsaksnummer") shouldBe ""
            ds.connection.getSkeKravIdent("3330-navsaksnummer") shouldBe ""
            ds.connection.getSkeKravIdent("4440-navsaksnummer") shouldBe "4444-skeUUID"
        }
    }

    test("getKravIdfromCorrId skal returnere krav_id basert på corr_id"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            ds.connection.getKravIdfromCorrId("CORR456") shouldBe 1
            ds.connection.getKravIdfromCorrId("CORR789") shouldBe 2
            ds.connection.getKravIdfromCorrId("CORR987") shouldBe 3
            ds.connection.getKravIdfromCorrId("CORR652") shouldBe 4
            ds.connection.getKravIdfromCorrId("CORR253") shouldBe 5
            ds.connection.getKravIdfromCorrId("CORR263482") shouldBe 6
            ds.connection.getKravIdfromCorrId("CORR83985902") shouldBe 7
            ds.connection.getKravIdfromCorrId("finnesikke") shouldBe 0
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og tidspunkt_sendt og tidspunkt_siste_status settes til NOW"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSendtKrav("CORR83985902", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.saksnummerSKE shouldBe "6666-skeUUID"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSendtKrav("CORR83985902", "NykravidentSke", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.saksnummerSKE shouldBe "NykravidentSke"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateSendtKrav skal oppdatere krav med ny status og ny corr_id, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW"){
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->

            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateSendtKrav("NYCORRID", "CORR83985902", "NYTT_KRAV", "TESTSTATUS")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "NYCORRID" }
            updatedKrav.status shouldBe "TESTSTATUS"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }
    }

    test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW"){
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

    test("insertAllNewKrav skal inserte alle kravlinjene"){
        val liste = readFileFromFS("FilMedBare10Linjer.txt".asResource())
        val kravlinjer = FileParser(liste).parseKravLinjer()
        startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql")).use { ds ->
            val originalSize = ds.connection.getAllKrav().size
            ds.connection.insertAllNewKrav(kravlinjer)

            ds.connection.getAllKrav().size shouldBe originalSize+kravlinjer.size
        }
    }

    test("setSkeKravIdentPaEndring skal sette kravidentifikator_ske med gitt saksnummer hvis kravet ikke er et nytt krav"){
       val ds = startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql"))

        ds.connection.use { con ->
            val originalNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "6660-navsaksnummer" }
            originalNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"

            con.setSkeKravIdentPaEndring("6660-navsaksnummer", "Ny_ske_saksnummer")

            val updatedNyttKrav = con.getAllKrav().first {  it.saksnummerNAV == "6660-navsaksnummer" }
            updatedNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"
        }

       ds.connection.use { con ->
           val originalStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
           originalStoppKrav.saksnummerSKE shouldBe "3333-skeUUID"

           con.setSkeKravIdentPaEndring("3330-navsaksnummer", "Ny_ske_saksnummer")

           val updatedStoppKrav = con.getAllKrav().first {  it.saksnummerNAV == "3330-navsaksnummer" }
           updatedStoppKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
       }

       ds.connection.use { con ->
           val originalEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
           originalEndreKrav.saksnummerSKE shouldBe "2222-skeUUID"

           con.setSkeKravIdentPaEndring("2220-navsaksnummer", "Ny_ske_saksnummer")

           val updatedEndreKrav = con.getAllKrav().first {  it.saksnummerNAV == "3330-navsaksnummer" }
           updatedEndreKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
       }
    }


    test("saveValidationError skal lagre valideringsfeil"){}

})

