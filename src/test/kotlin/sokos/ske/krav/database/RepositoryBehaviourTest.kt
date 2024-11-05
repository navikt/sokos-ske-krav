package sokos.ske.krav.database

import io.kotest.core.spec.style.BehaviorSpec
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
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.getAllKrav
import java.time.LocalDate
import java.time.LocalDateTime

internal class RepositoryBehaviourTest :
    BehaviorSpec({
        given("det finnes krav som skal oppdateres") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalOppdateres.sql")

            then("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {

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

            then("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {

                testContainer.dataSource.connection.use { con ->
                    val originalKrav = con.getAllKrav().first { it.corrId == "CORR457387" }
                    originalKrav.status shouldBe "RESKONTROFOERT"
                    originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                    con.updateStatus("NY_STATUS", "CORR457387")

                    val updatedKrav = con.getAllKrav().first { it.corrId == "CORR457387" }
                    updatedKrav.status shouldBe "NY_STATUS"
                    updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
                }
            }

            then("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummer") {
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
        }

        given("det finnes krav som skal resendes") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalResendes.sql")

            then("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
                testContainer.dataSource.connection.use { it.getAllKravForStatusCheck().size shouldBe 5 }
            }
            then(
                "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
            ) {
                testContainer.dataSource.connection.use { it.getAllKravForResending().size shouldBe 9 }
            }
            then("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
                testContainer.dataSource.connection.use { it.getAllUnsentKrav().size shouldBe 3 }
            }
            then("getSkeKravIdent skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
                testContainer.dataSource.connection.use { it.getSkeKravidentifikator("2220-navsaksnummer") shouldBe "1111-skeUUID" }
                testContainer.dataSource.connection.use { it.getSkeKravidentifikator("3330-navsaksnummer") shouldBe "3333-skeUUID" }
                testContainer.dataSource.connection.use { it.getSkeKravidentifikator("4440-navsaksnummer") shouldBe "4444-skeUUID" }
                testContainer.dataSource.connection.use { it.getSkeKravidentifikator("1111-navsaksnummer") shouldBe "" }
                testContainer.dataSource.connection.use { it.getSkeKravidentifikator("1113-navsaksnummer") shouldBe "1112-skeUUID" }
            }
            then("getPreviousOldRef skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
                testContainer.dataSource.connection.use { it.getPreviousReferansenummer("2220-navsaksnummer") shouldBe "1110-navsaksnummer" }
                testContainer.dataSource.connection.use { it.getPreviousReferansenummer("foo-navsaksnummer") shouldBe "foo-navsaksnummer" }
            }

            then("getKravIdfromCorrId skal returnere krav_id basert på corr_id") {
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR456") shouldBe 1 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR789") shouldBe 2 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR987") shouldBe 3 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR652") shouldBe 4 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR253") shouldBe 5 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR263482") shouldBe 6 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("CORR83985902") shouldBe 7 }
                testContainer.dataSource.connection.use { it.getKravTableIdFromCorrelationId("finnesikke") shouldBe 0 }
            }

            then("updateSentKrav skal oppdatere krav med ny status og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {

                testContainer.migrate("KravSomSkalResendes.sql")
                testContainer.dataSource.connection.use { con ->
                    val originalKrav = con.getAllKrav().first { it.corrId == "CORR83985902" }
                    originalKrav.status shouldBe "RESKONTROFOERT"
                    originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                    originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                    con.updateSentKrav("CORR83985902", "TESTSTATUS")

                    val updatedKrav = con.getAllKrav().first { it.corrId == "CORR83985902" }
                    updatedKrav.status shouldBe "TESTSTATUS"
                    updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                    updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
                }
            }
        }

        given("det finnes feilmeldinger") {
            val testContainer = TestContainer()
            testContainer.migrate("Feilmeldinger.sql")

            then("getAllErrorMessages skal returnere alle feilmeldinger ") {
                testContainer.dataSource.connection.use { it.getAllFeilmeldinger().size shouldBe 3 }
            }

            then("getErrorMessageForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
                val feilmelding1 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(1) }
                feilmelding1.size shouldBe 1
                feilmelding1.first().corrId shouldBe "CORR856"
                val feilmelding2 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(2) }
                feilmelding2.size shouldBe 2
                feilmelding2.filter { it.error == "404" }.size shouldBe 1
                feilmelding2.filter { it.error == "422" }.size shouldBe 1
            }
            then("insertErrorMessage skal lagre feilmelding") {
                val feilmelding =
                    FeilmeldingTable(
                        2L,
                        1L,
                        "CORR456",
                        "1110-navsaksnummer",
                        "1111-skeUUID",
                        "409",
                        "feilmelding 409 1111",
                        "{nav request2}",
                        "{ske response 2}",
                        LocalDateTime.now(),
                    )

                testContainer.dataSource.connection.use { con ->
                    con.getAllFeilmeldinger().size shouldBe 3
                    con.insertFeilmelding(feilmelding)

                    val feilmeldinger = con.getAllFeilmeldinger()
                    feilmeldinger.size shouldBe 4
                    feilmeldinger.filter { it.kravId == 1L }.size shouldBe 2
                    feilmeldinger.filter { it.corrId == "CORR456" }.size shouldBe 1
                    feilmeldinger.filter { it.corrId == "CORR856" }.size shouldBe 1
                    feilmeldinger.filter { it.corrId == "CORR658" }.size shouldBe 2
                }
            }
        }

        given("det finnes krav som skal avstemmes") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalAvstemmes.sql")
            testContainer.migrate("FeilmeldingerSomSkalAvstemmes.sql")

            then("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
                testContainer.dataSource.connection.use { it.getAllKravForAvstemming().size shouldBe 3 }
            }

            then("updateStatusForAvstemtKravToReported skal sette status til VALIDERINGFEIL_RAPPORTERT på krav med angitt kravid") {
                val kravForAvstemmingBeforeUpdate = testContainer.dataSource.connection.use { it.getAllKravForAvstemming() }

                val firstKrav = kravForAvstemmingBeforeUpdate.first()
                val lastKrav = kravForAvstemmingBeforeUpdate.last()

                testContainer.dataSource.connection.use { it.updateStatusForAvstemtKravToReported(firstKrav.kravId.toInt()) }
                testContainer.dataSource.connection.use { it.updateStatusForAvstemtKravToReported(lastKrav.kravId.toInt()) }

                val kravForAvstemmingAfterUpdate = testContainer.dataSource.connection.use { it.getAllKravForAvstemming() }
                kravForAvstemmingAfterUpdate.size shouldBe kravForAvstemmingBeforeUpdate.size - 2

                val feilmelding1 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(firstKrav.kravId) }
                val feilmelding2 = testContainer.dataSource.connection.use { it.getFeilmeldingForKravId(lastKrav.kravId) }

                feilmelding1.first().rapporter shouldBe false
                feilmelding2.first().rapporter shouldBe false
            }
        }
    })
