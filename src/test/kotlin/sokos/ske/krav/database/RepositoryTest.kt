package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.database.Repository.getAllFeilmeldinger
import sokos.ske.krav.database.Repository.getValideringsFeilForLinje
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertFeilmelding
import sokos.ske.krav.database.Repository.insertLineValideringsfeil
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.getAllKrav
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

internal class RepositoryTest :
    FunSpec({

        test("insertAllNewKrav skal inserte alle kravlinjene") {

            val filnavn = "${File.separator}FtpFiler${File.separator}8NyeKrav1Endring1Stopp.txt"
            val liste = fileAsList(filnavn)
            val kravlinjer = FileParser(liste).parseKravLinjer()
            val testContainer = TestContainer()
            testContainer.dataSource.connection.use { con ->
                con.insertAllNewKrav(kravlinjer, filnavn)
                val lagredeKrav = con.getAllKrav()
                lagredeKrav.size shouldBe kravlinjer.size + 1
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1
            }
        }

        test("insertFeilmelding skal lagre feilmelding") {
            val testContainer = TestContainer()
            testContainer.migrate("Feilmeldinger.sql")
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

        test("insertValideringsfeil skal lagre valideringsfeil") {
            val fileName = this.testCase.name.testName
            val feilMelding = "Test validation error insert"
            val linje =
                KravLinje(
                    1,
                    "saksnr",
                    BigDecimal.valueOf(123.45),
                    LocalDate.now(),
                    "gjelderID",
                    "2001-01-01",
                    "2002-02-02",
                    "FA FA",
                    "refnr",
                    "2003-03-03",
                    "1234",
                    "5678",
                    "FT",
                    "FU",
                    BigDecimal.valueOf(123.45),
                    BigDecimal.valueOf(678.90),
                    LocalDate.now(),
                    "fagid",
                    "NYTT_KRAV",
                )

            TestContainer().dataSource.connection.use { con ->
                val rsBefore = con.prepareStatement("""select count(*) from valideringsfeil""").executeQuery()
                rsBefore.next()
                rsBefore.getInt("count") shouldBe 0

                con.insertLineValideringsfeil(fileName, linje, feilMelding)

                val rsAfter = con.prepareStatement("""select count(*) from valideringsfeil""").executeQuery()
                rsAfter.next()
                rsAfter.getInt("count") shouldBe 1

                val savedErrorRs = con.prepareStatement("""select * from valideringsfeil""").executeQuery()
                savedErrorRs.next()
                savedErrorRs.getString("filnavn") shouldBe fileName
                savedErrorRs.getString("linjenummer") shouldBe linje.linjenummer.toString()
                savedErrorRs.getString("saksnummer_nav") shouldBe linje.saksnummerNav
                savedErrorRs.getString("kravlinje") shouldBe linje.toString()
                savedErrorRs.getString("feilmelding") shouldBe feilMelding
            }
        }

        test("getValideringsFeilForKravId skal returnere en liste av ValideringsFeil knyttet til gitt KravID") {
            val testContainer = TestContainer()
            testContainer.migrate("ValideringsFeil.sql")
            val kravtable1 =
                mockk<KravTable>(relaxed = true) {
                    every { filnavn } returns "Fil1.txt"
                    every { linjenummer } returns 1
                }
            val kravtable2 =
                mockk<KravTable>(relaxed = true) {
                    every { filnavn } returns "Fil2.txt"
                    every { linjenummer } returns 2
                }
            val kravtable3 =
                mockk<KravTable>(relaxed = true) {
                    every { filnavn } returns "Fil3.txt"
                    every { linjenummer } returns 3
                }

            testContainer.dataSource.connection.use { con ->
                with(con.getValideringsFeilForLinje(kravtable1.filnavn, kravtable1.linjenummer)) {
                    size shouldBe 1
                    with(first()) {
                        valideringsfeilId shouldBe 1
                        filnavn shouldBe "Fil1.txt"
                        linjenummer shouldBe 1
                        saksnummerNav shouldBe "111"
                        kravLinje shouldBe "linje1"
                        feilmelding shouldBe "feilmelding1"
                    }
                }
            }

            testContainer.dataSource.connection.use { con ->
                with(con.getValideringsFeilForLinje(kravtable2.filnavn, kravtable2.linjenummer)) {
                    size shouldBe 2
                    with(get(0)) {
                        valideringsfeilId shouldBe 21
                        filnavn shouldBe "Fil2.txt"
                        linjenummer shouldBe 2
                        saksnummerNav shouldBe "222"
                        kravLinje shouldBe "linje2.1"
                        feilmelding shouldBe "feilmelding2.1"
                    }
                    with(get(1)) {
                        valideringsfeilId shouldBe 22
                        filnavn shouldBe "Fil2.txt"
                        linjenummer shouldBe 2
                        saksnummerNav shouldBe "222"
                        kravLinje shouldBe "linje2.2"
                        feilmelding shouldBe "feilmelding2.2"
                    }
                }
            }
            testContainer.dataSource.connection.use { con ->
                with(con.getValideringsFeilForLinje(kravtable3.filnavn, kravtable3.linjenummer)) {
                    size shouldBe 3
                    with(get(0)) {
                        valideringsfeilId shouldBe 31
                        filnavn shouldBe "Fil3.txt"
                        linjenummer shouldBe 3
                        saksnummerNav shouldBe "333"
                        kravLinje shouldBe "linje3.1"
                        feilmelding shouldBe "feilmelding3.1"
                    }
                    with(get(1)) {
                        valideringsfeilId shouldBe 32
                        filnavn shouldBe "Fil3.txt"
                        linjenummer shouldBe 3
                        saksnummerNav shouldBe "333"
                        kravLinje shouldBe "linje3.2"
                        feilmelding shouldBe "feilmelding3.2"
                    }
                    with(get(2)) {
                        valideringsfeilId shouldBe 33
                        filnavn shouldBe "Fil3.txt"
                        linjenummer shouldBe 3
                        saksnummerNav shouldBe "333"
                        kravLinje shouldBe "linje3.3"
                        feilmelding shouldBe "feilmelding3.3"
                    }
                }
            }
        }
    })
