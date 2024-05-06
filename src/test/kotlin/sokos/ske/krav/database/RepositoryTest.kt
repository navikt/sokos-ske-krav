package sokos.ske.krav.database

import io.kotest.assertions.throwables.shouldThrow
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
import sokos.ske.krav.database.Repository.getPreviousOldRef
import sokos.ske.krav.database.Repository.getSkeKravidentifikator
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertErrorMessage
import sokos.ske.krav.database.Repository.insertValidationError
import sokos.ske.krav.database.Repository.updateEndringWithSkeKravIdentifikator
import sokos.ske.krav.database.Repository.updateSentKrav
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.Repository.updateStatusForAvstemtKravToReported
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.asResource
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.readFileFromFS
import sokos.ske.krav.util.startContainer
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


internal class RepositoryTest : FunSpec({
    val kravSomSkalResendesDB = startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalResendes.sql"))
    val feilmeldingerDB = startContainer(UUID.randomUUID().toString(), listOf("Feilmeldinger.sql"))
    val kravSomSkalAvstemmesDB = startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalAvstemmes.sql"))
    val emptyDB = startContainer(UUID.randomUUID().toString(), emptyList())

    test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
        kravSomSkalResendesDB.connection.getAllKravForStatusCheck().size shouldBe 5

    }
    test("getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ") {
        kravSomSkalResendesDB.connection.getAllKravForResending().size shouldBe 9
    }
    test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
        kravSomSkalResendesDB.connection.getAllUnsentKrav().size shouldBe 3
    }
    test("getAllValidationErrors skal returnere krav som har status VALIDERINGSFEIL_422") {
        kravSomSkalResendesDB.connection.getAllValidationErrors().size shouldBe 1
    }

    test("getAllErrorMessages skal returnere alle feilmeldinger ") {
        feilmeldingerDB.connection.getAllErrorMessages().size shouldBe 3
    }

    test("getErrorMessageForKravId skal returnere en liste med feilmeldinger for angitt kravid") {

        val feilmelding1 = feilmeldingerDB.connection.getErrorMessageForKravId(1)
        feilmelding1.size shouldBe 1
        feilmelding1.first().corrId shouldBe "CORR856"
        val feilmelding2 = feilmeldingerDB.connection.getErrorMessageForKravId(2)
        feilmelding2.size shouldBe 2
        feilmelding2.filter { it.error == "404" }.size shouldBe 1
        feilmelding2.filter { it.error == "422" }.size shouldBe 1

    }

    test("getAllKravForAvstemming skal returnere alle krav som ikke har status RESKONTROFOERT eller VALIDERINGFEIL_RAPPORTERT") {
        kravSomSkalAvstemmesDB.connection.getAllKravForAvstemming().size shouldBe 9
    }

    test("getSkeKravIdent skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
        kravSomSkalResendesDB.connection.getSkeKravidentifikator("2220-navsaksnummer") shouldBe "1111-skeUUID"
        kravSomSkalResendesDB.connection.getSkeKravidentifikator("3330-navsaksnummer") shouldBe "3333-skeUUID"
        kravSomSkalResendesDB.connection.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID"
        kravSomSkalResendesDB.connection.getSkeKravidentifikator("1111-navsaksnummer") shouldBe ""
        kravSomSkalResendesDB.connection.getSkeKravidentifikator("1113-navsaksnummer") shouldBe "1112-skeUUID"

    }

    test("getPreviousOldRef skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
        kravSomSkalResendesDB.connection.getPreviousOldRef("2220-navsaksnummer") shouldBe "1110-navsaksnummer"
    }

    test("getKravIdfromCorrId skal returnere krav_id basert på corr_id") {
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR456") shouldBe 1
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR789") shouldBe 2
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR987") shouldBe 3
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR652") shouldBe 4
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR253") shouldBe 5
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR263482") shouldBe 6
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("CORR83985902") shouldBe 7
        kravSomSkalResendesDB.connection.getKravTableIdFromCorrelationId("finnesikke") shouldBe 0

    }

    test("updateSentKrav skal oppdatere krav med ny status og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
        startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalResendes.sql")).use { ds ->
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

        startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalResendes.sql")).use { ds ->
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
        startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalResendes.sql")).use { ds ->
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
        startContainer(UUID.randomUUID().toString(), listOf("KravSomSkalResendes.sql")).use { ds ->
            val originalKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            originalKrav.status shouldBe "RESKONTROFOERT"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            ds.connection.updateStatus("NY_STATUS", "CORR83985902")

            val updatedKrav = ds.connection.getAllKrav().first { it.corr_id == "CORR83985902" }
            updatedKrav.status shouldBe "NY_STATUS"
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }


    }

    test("updateStatusForAvstemtKravToReported skal sette status til VALIDERINGFEIL_RAPPORTERT på krav med angitt kravid") {

        val kravForAvstemmingBeforeUpdate = kravSomSkalAvstemmesDB.connection.getAllKravForAvstemming()

        val firstKrav = kravForAvstemmingBeforeUpdate.first()
        val lastKrav = kravForAvstemmingBeforeUpdate.last()
        firstKrav.status shouldNotBe Status.VALIDERINGFEIL_RAPPORTERT.value
        lastKrav.status shouldNotBe Status.VALIDERINGFEIL_RAPPORTERT.value


        kravSomSkalAvstemmesDB.connection.updateStatusForAvstemtKravToReported(firstKrav.kravId.toInt())
        kravSomSkalAvstemmesDB.connection.updateStatusForAvstemtKravToReported(lastKrav.kravId.toInt())


        val kravForAvstemmingAfterUpdate = kravSomSkalAvstemmesDB.connection.getAllKravForAvstemming()
        kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2
        kravForAvstemmingAfterUpdate.filter { it.status == Status.VALIDERINGFEIL_RAPPORTERT.value }.size shouldBe 0

        val alleKrav = kravSomSkalAvstemmesDB.connection.getAllKrav()
        val firstKravAfterUpdate = alleKrav.find { it.kravId == firstKrav.kravId }
        val lastKravAfterUpdate = alleKrav.find { it.kravId == lastKrav.kravId }

        firstKravAfterUpdate?.status shouldBe Status.VALIDERINGFEIL_RAPPORTERT.value
        lastKravAfterUpdate?.status shouldBe Status.VALIDERINGFEIL_RAPPORTERT.value

    }

    test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummer hvis kravet ikke er et nytt krav") {


        kravSomSkalResendesDB.connection.use { con ->
            val originalNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "6660-navsaksnummer" }
            originalNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("6660-navsaksnummer", "Ny_ske_saksnummer")

            val updatedNyttKrav = con.getAllKrav().first { it.saksnummerNAV == "6660-navsaksnummer" }
            updatedNyttKrav.saksnummerSKE shouldBe "6666-skeUUID"
        }

        kravSomSkalResendesDB.connection.use { con ->
            val originalStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            originalStoppKrav.saksnummerSKE shouldBe "3333-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("3330-navsaksnummer", "Ny_ske_saksnummer")

            val updatedStoppKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedStoppKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
        }

        kravSomSkalResendesDB.connection.use { con ->
            val originalEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "2220-navsaksnummer" }
            originalEndreKrav.saksnummerSKE shouldBe "1111-skeUUID"

            con.updateEndringWithSkeKravIdentifikator("2220-navsaksnummer", "Ny_ske_saksnummer")

            val updatedEndreKrav = con.getAllKrav().first { it.saksnummerNAV == "3330-navsaksnummer" }
            updatedEndreKrav.saksnummerSKE shouldBe "Ny_ske_saksnummer"
        }
    }



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

    test("insertErrorMessage skal lagre feilmelding") {
        val feilmelding = FeilmeldingTable(
            2L, 1L, "CORR456", "1110-navsaksnummer", "1111-skeUUID",
            "409", "feilmelding 409 1111", "{nav request2}", "{ske response 2}", LocalDateTime.now()
        )

        feilmeldingerDB.use { ds ->
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

    test("insertValidationError skal lagre valideringsfeil") {
        val fileName = this.testCase.name.testName
        val feilMelding = "Test validation error insert"
        val linje = KravLinje(1, "saksnr", BigDecimal.valueOf(123.45), LocalDate.now(), "gjelderID", "2001-01-01", "2002-02-02", "FA FA", "refnr", "2003-03-03", "1234", "5678", "FT", "FU", BigDecimal.valueOf(123.45), BigDecimal.valueOf(678.90), LocalDate.now(), "fagid", "NYTT_KRAV")

        emptyDB.connection.use { con ->
            val rsBefore = con.prepareStatement(
                """select count(*) from valideringsfeil"""
            ).executeQuery()
            rsBefore.next()
            rsBefore.getInt("count") shouldBe 0

            con.insertValidationError(fileName, linje, feilMelding)

            val rsAfter = con.prepareStatement(
                """select count(*) from valideringsfeil"""
            ).executeQuery()
            rsAfter.next()
            rsAfter.getInt("count") shouldBe 1

            val savedErrorRs = con.prepareStatement(
                """select * from valideringsfeil"""
            ).executeQuery()
            savedErrorRs.next()
            savedErrorRs.getString("filnavn") shouldBe fileName
            savedErrorRs.getString("linjenr") shouldBe linje.linjeNummer.toString()
            savedErrorRs.getString("saksnr") shouldBe linje.saksNummer
            savedErrorRs.getString("kravlinje") shouldBe linje.toString()
            savedErrorRs.getString("feilmelding") shouldBe feilMelding

        }
    }

    test("getColumn skal kaste exception hvis den ikke kan parse datatypen") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.getColumn("any")
            }
        }
    }

    test("resultset getcolumn skal kaste exception hvis påkrevd column er null") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                it.prepareStatement(
                    """
                    insert into feilmelding ( kravID, corr_id, saksnummer, kravidentifikator_ske, error, melding, navRequest, skeResponse, dato)
                    values  (1, 'CORR769', '3330-navsaksnummer', '3333-skeUUID', 422, 'feilmelding 422 3333', '{nav request 3}', '{ske response 3}',  null);
                """.trimIndent()
                ).execute()
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.toFeilmelding()
            }
        }
    }

    test("resultset getcolumn skal kaste exception hvis den ikke finner kolonne med det gitte navnet") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.getColumn("foo")
            }
        }
    }

    test("useAndHandleErrors skal kaste exception oppover") {
        shouldThrow<SQLException> {
            emptyDB.connection.useAndHandleErrors {
                it.prepareStatement("""insert into foo values(1,2)""").execute()
            }
        }
    }
})

