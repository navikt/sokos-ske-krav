package no.nav.sokos.ske.krav.database

import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.repository.KravRepository.deleteOldKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForAvstemming
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForResending
import no.nav.sokos.ske.krav.repository.KravRepository.getAllKravForStatusCheck
import no.nav.sokos.ske.krav.repository.KravRepository.getAllUnsentEndringerAndStopp
import no.nav.sokos.ske.krav.repository.KravRepository.getAllUnsentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.getPreviousReferansenummer
import no.nav.sokos.ske.krav.repository.KravRepository.getSkeKravidentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.insertAllNewKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateEndringWithSkeKravIdentifikator
import no.nav.sokos.ske.krav.repository.KravRepository.updateSentKrav
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatus
import no.nav.sokos.ske.krav.repository.KravRepository.updateStatusForAvstemtKravToReported
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
            val allKrav = DBListener.dataSource.transaction { getAllKravForStatusCheck(it) }
            allKrav.shouldHaveSize(5)
        }

        test(
            "getAllKravForResending skal returnere krav som har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 ",
        ) {
            val kravForResending = DBListener.dataSource.transaction { getAllKravForResending(it) }

            kravForResending.shouldHaveSize(10)
            kravForResending.shouldForAll {
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
            val unsentKrav = DBListener.dataSource.transaction { getAllUnsentKrav(it) }

            unsentKrav.shouldHaveSize(4)
            unsentKrav.shouldForAll {
                it.status shouldBe Status.KRAV_IKKE_SENDT
            }
        }

        test("getAllKravForAvstemming skal returnere alle krav som har en feilmelding med status rapporter=true") {
            DBListener.loadInitScript("SQLscript/feilmeldinger/Feilmeldinger.sql")

            val kravForAvstemming = DBListener.dataSource.transaction { getAllKravForAvstemming(it) }
            kravForAvstemming.shouldHaveSize(4)
        }

        test("getSkeKravidentifikator skal returnere kravidentifikator_ske basert på saksnummer_nav eller gammel referanse") {
            DBListener.dataSource.transaction {
                getSkeKravidentifikator(it, "1010-navsaksnummer") shouldBe "1010-skeUUID"
                getSkeKravidentifikator(it, "1111-navsaksnummer") shouldBe ""
                getSkeKravidentifikator(it, "1112-navsaksnummer") shouldBe "1112-skeUUID"
                getSkeKravidentifikator(it, "1113-navsaksnummer") shouldBe "1112-skeUUID"
                getSkeKravidentifikator(it, "4440-navsaksnummer") shouldBe "4444-skeUUID"
            }
        }
        test("getPreviousReferansenummer skal returnere den tidligste referansenummergammelsak basert på saksnummer_nav") {
            DBListener.dataSource.transaction {
                getPreviousReferansenummer(it, "2220-navsaksnummer") shouldBe "1110-navsaksnummer"
                getPreviousReferansenummer(it, "foo-navsaksnummer") shouldBe "foo-navsaksnummer"
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

        test("getAllUnsentEndringerAndStopp skal returnere alle endringer og stopp som er lest inn men ikke sendt") {
            val krav = DBListener.dataSource.transaction { getAllUnsentEndringerAndStopp(it) }

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
            DBListener.dataSource.transaction {
                val originalKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrID }
                originalKrav.status shouldBe Status.RESKONTROFOERT
                originalKrav.tidspunktSendt?.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                updateSentKrav(it, corrID, Status.KRAV_SENDT)
                val updatedKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrID }
                updatedKrav.status shouldBe Status.KRAV_SENDT
                updatedKrav.tidspunktSendt?.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.kravidentifikatorSKE shouldBe originalKrav.kravidentifikatorSKE
            }
        }

        test("updateSendtKrav skal oppdatere krav med ny status og ny kravidentifikator_ske, og tidspunkt_sendt og tidspunkt_siste_status settes til NOW") {
            val corrID = "CORR83985902"
            DBListener.dataSource.transaction {
                val originalKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrID }
                originalKrav.status shouldBe Status.RESKONTROFOERT
                originalKrav.kravidentifikatorSKE shouldBe "6666-skeUUID"
                originalKrav.tidspunktSendt!!.toString() shouldBe "2023-02-01T12:00"
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                updateSentKrav(it, corrID, Status.KRAV_SENDT, "NykravidentSke")
                val updatedKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrID }
                updatedKrav.status shouldBe Status.KRAV_SENDT
                updatedKrav.kravidentifikatorSKE shouldBe "NykravidentSke"
                updatedKrav.tidspunktSendt!!.toLocalDate() shouldBe LocalDate.now()
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatus skal oppdatere status, og tidspunkt_siste_status skal settes til NOW") {
            val corrId = "CORR457389"
            DBListener.dataSource.transaction {
                val originalKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrId }
                originalKrav.status shouldBe Status.RESKONTROFOERT
                originalKrav.tidspunktSisteStatus.toString() shouldBe "2023-02-01T13:00"

                updateStatus(it, Status.KRAV_IKKE_SENDT, corrId)
                val updatedKrav = KravRepository.getAllKrav(it).first { krav -> krav.corrId == corrId }
                updatedKrav.status shouldBe Status.KRAV_IKKE_SENDT
                updatedKrav.tidspunktSisteStatus.toLocalDate() shouldBe LocalDate.now()
            }
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            DBListener.loadInitScript("SQLscript/feilmeldinger/Feilmeldinger.sql")

            DBListener.dataSource.transaction { tx ->
                val kravForAvstemmingBeforeUpdate = getAllKravForAvstemming(tx)
                val firstKrav = kravForAvstemmingBeforeUpdate.first()
                val lastKrav = kravForAvstemmingBeforeUpdate.last()

                updateStatusForAvstemtKravToReported(tx, firstKrav.kravId.toInt())
                updateStatusForAvstemtKravToReported(tx, lastKrav.kravId.toInt())

                val kravForAvstemmingAfterUpdate = getAllKravForAvstemming(tx)
                kravForAvstemmingAfterUpdate.shouldHaveSize(kravForAvstemmingBeforeUpdate.size - 2)

                val feilmelding1 = FeilmeldingRepository.getFeilmeldingForKravId(tx, firstKrav.kravId)
                val feilmelding2 = FeilmeldingRepository.getFeilmeldingForKravId(tx, lastKrav.kravId)

                feilmelding1.first().rapporter shouldBe false
                feilmelding2.first().rapporter shouldBe false
            }
        }

        test("updateEndringWithSkeKravIdentifikator skal sette kravidentifikator_ske med gitt saksnummerNav") {
            DBListener.dataSource.transaction { tx ->
                val nyttKravSaksnummerNAV = "7770-navsaksnummer"
                getSkeKravidentifikator(tx, nyttKravSaksnummerNAV) shouldBe "7777-skeUUID"
                updateEndringWithSkeKravIdentifikator(tx, nyttKravSaksnummerNAV, "ny_saksnummer_nytt_krav")
                getSkeKravidentifikator(tx, nyttKravSaksnummerNAV) shouldBe "7777-skeUUID"

                val stoppKravSaksnummerNAV = "3330-navsaksnummer"
                getSkeKravidentifikator(tx, stoppKravSaksnummerNAV) shouldBe "3333-skeUUID"
                updateEndringWithSkeKravIdentifikator(tx, stoppKravSaksnummerNAV, "ny_saksnummer_stopp_krav")
                getSkeKravidentifikator(tx, stoppKravSaksnummerNAV) shouldBe "ny_saksnummer_stopp_krav"

                val endreKravSaksnummerNAV = "2220-navsaksnummer"
                getSkeKravidentifikator(tx, endreKravSaksnummerNAV) shouldBe "1111-skeUUID"
                updateEndringWithSkeKravIdentifikator(tx, endreKravSaksnummerNAV, "ny_saksnummer_endre_krav")
                getSkeKravidentifikator(tx, endreKravSaksnummerNAV) shouldBe "ny_saksnummer_endre_krav"
            }
        }

        test("insertAllNewKrav skal inserte alle kravlinjene") {
            DBListener.clearDB()

            val filnavn = "TiNyeKrav1Endring1Stopp.txt"
            val liste = getFileContent("krav/$filnavn")
            val kravlinjer = FileParser(liste).parseKravLinjer()

            DBListener.dataSource.transaction { tx ->
                insertAllNewKrav(tx, kravlinjer, filnavn)

                val lagredeKrav = KravRepository.getAllKrav(tx)
                lagredeKrav.shouldHaveSize(kravlinjer.size + 1)
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.shouldHaveSize(8)
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.shouldHaveSize(1)
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.shouldHaveSize(1)
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.shouldHaveSize(1)
            }
        }

        test("deleteOldKrav skal slette alle kravene som ble opprettet før en spesifisert tid") {
            DBListener.dataSource.transaction {
                val threshold = LocalDate.parse("2023-01-02")
                val kravDeleted = deleteOldKrav(it, threshold)
                kravDeleted shouldBe 18
            }
        }

        afterTest {
            DBListener.clearDB()
        }
    })

fun KravRepository.getAllKrav(tx: TransactionalSession): List<Krav> =
    tx.list(
        queryOf("select * from krav"),
        mapToKrav,
    )
