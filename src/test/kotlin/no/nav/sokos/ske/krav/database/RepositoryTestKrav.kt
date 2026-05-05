package no.nav.sokos.ske.krav.database

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.util.DBUtils.transaction
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class RepositoryTestKrav :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScripts("SQLscript/krav/KravForRepositoryTest.sql")
        }

        test("getAllKravForStatusCheck skal returnere krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING") {
            val allKrav = kravRepository.getAllKravForStatusCheck()
            allKrav.shouldHaveSize(5)
        }

        test(
            "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
        ) {
            val kravForResending = kravRepository.getAllKravForResending()

            kravForResending.shouldHaveSize(10)
            kravForResending.forAll {
                it.status.shouldBeIn(
                    Status.KRAV_IKKE_SENDT,
                    Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND,
                    Status.HTTP500_ANNEN_SERVER_FEIL,
                    Status.HTTP503_UTILGJENGELIG_TJENESTE,
                    Status.HTTP500_INTERN_TJENERFEIL,
                )
            }
        }

        test("getAllUnsentKrav skal returnere krav som har status KRAV_IKKE_SENDT") {
            val unsentKrav = kravRepository.getAllUnsentKrav()

            unsentKrav.shouldHaveSize(4)
            unsentKrav.forAll {
                it.status shouldBe Status.KRAV_IKKE_SENDT
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            DBListener.loadInitScripts("SQLscript/feilmeldinger/Feilmeldinger.sql")

            val kravForAvstemming = kravRepository.getAllKravForAvstemming()
            kravForAvstemming.shouldHaveSize(4)
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

        test("getAllUnsentEndringerAndStopp skal returnere alle endringer og stopp som er lest inn men ikke sendt") {
            val krav = kravRepository.getAllUnsentEndringerAndStopp()

            krav.size shouldBe 3
            krav.count { it.kravtype == STOPP_KRAV } shouldBe 1
            krav.count { it.kravtype == ENDRING_HOVEDSTOL } shouldBe 1
            krav.count { it.kravtype == ENDRING_RENTE } shouldBe 1
            krav.forEach { krav ->
                krav.status shouldBe Status.KRAV_IKKE_SENDT
                krav.tidspunktSendt shouldBe null
            }
        }

        test("updateSentKrav skal oppdatere krav med ny status, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val corrID = "CORR457387"

            val originalKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrID }
            originalKrav.status shouldBe Status.RESKONTROFOERT
            originalKrav.tidspunktSendt?.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            kravRepository.updateSentKrav(corrID, Status.KRAV_SENDT)
            val updatedKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrID }
            updatedKrav.status shouldBe Status.KRAV_SENDT
            updatedKrav.tidspunktSendt?.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.kravidentifikatorSKE shouldBe originalKrav.kravidentifikatorSKE
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val corrID = "CORR83985902"
            val originalKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrID }
            originalKrav.status shouldBe Status.RESKONTROFOERT
            originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
            originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
            originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

            kravRepository.updateSentKrav(corrID, Status.KRAV_SENDT, "NykravidentSke")
            val updatedKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrID }
            updatedKrav.status shouldBe Status.KRAV_SENDT
            updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
            updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
            updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            val corrId = "CORR457389"
            DBListener.dataSource.transaction {
                val originalKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrId }
                originalKrav.status shouldBe Status.RESKONTROFOERT
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                kravRepository.updateStatus(Status.KRAV_IKKE_SENDT, corrId)
                val updatedKrav = kravRepository.getAllKrav().first { krav -> krav.corrId == corrId }
                updatedKrav.status shouldBe Status.KRAV_IKKE_SENDT
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            val nyttKravSaksnummerNAV = "7770-navsaksnummer"
            kravRepository.getSkeKravidentifikator(nyttKravSaksnummerNAV) shouldBe "7777-skeUUID"
            kravRepository.updateEndringWithSkeKravIdentifikator(nyttKravSaksnummerNAV, "ny_saksnummer_nytt_krav")
            kravRepository.getSkeKravidentifikator(nyttKravSaksnummerNAV) shouldBe "7777-skeUUID"

            val stoppKravSaksnummerNAV = "3330-navsaksnummer"
            kravRepository.getSkeKravidentifikator(stoppKravSaksnummerNAV) shouldBe "3333-skeUUID"
            kravRepository.updateEndringWithSkeKravIdentifikator(stoppKravSaksnummerNAV, "ny_saksnummer_stopp_krav")
            kravRepository.getSkeKravidentifikator(stoppKravSaksnummerNAV) shouldBe "ny_saksnummer_stopp_krav"

            val endreKravSaksnummerNAV = "2220-navsaksnummer"
            kravRepository.getSkeKravidentifikator(endreKravSaksnummerNAV) shouldBe "1111-skeUUID"
            kravRepository.updateEndringWithSkeKravIdentifikator(endreKravSaksnummerNAV, "ny_saksnummer_endre_krav")
            kravRepository.getSkeKravidentifikator(endreKravSaksnummerNAV) shouldBe "ny_saksnummer_endre_krav"
        }

        test("insertAllNewKrav skal inserte alle kravlinjene") {
            DBListener.clearDB()

            val filnavn = "TiNyeKrav1Endring1Stopp.txt"
            val liste = getFileContent("krav/$filnavn")
            val kravlinjer = FileParser(liste).parseKravLinjer()

            kravRepository.insertAllNewKrav(kravlinjer, filnavn)

            val lagredeKrav = kravRepository.getAllKrav()
            lagredeKrav.shouldHaveSize(kravlinjer.size + 1)
            lagredeKrav.filter { it.kravtype == NYTT_KRAV }.shouldHaveSize(8)
            lagredeKrav.filter { it.kravtype == STOPP_KRAV }.shouldHaveSize(1)
            lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.shouldHaveSize(1)
            lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.shouldHaveSize(1)
        }

        test("deleteOldKrav skal slette alle kravene som ble opprettet før en spesifisert tid") {
            val threshold = LocalDate.parse("2023-01-02")
            val kravDeleted = kravRepository.deleteOldKrav(threshold)
            kravDeleted shouldBe 18
        }

        afterTest {
            DBListener.clearDB()
        }
    })

fun KravRepository.getAllKrav(): List<Krav> =
    transaction { session ->
        session.list(
            queryOf("select * from krav"),
            extractor = mapToKrav,
        )
    }
