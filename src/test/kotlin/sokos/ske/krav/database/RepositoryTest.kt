/*
package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.Repository.getAllFeilmeldinger
import sokos.ske.krav.database.Repository.insertAllNewKrav
import sokos.ske.krav.database.Repository.insertFeilmelding
import sokos.ske.krav.database.Repository.insertValidationError
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.nav.FileParser
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.util.FtpTestUtil.fileAsList
import sokos.ske.krav.util.containers.TestContainer
import sokos.ske.krav.util.getAllKrav
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

internal class RepositoryTest :
    FunSpec({
        extensions(TestContainer)

        test("insertAllNewKrav skal inserte alle kravlinjene") {
            val filnavn = "${File.separator}FtpFiler${File.separator}8NyeKrav1Endring1Stopp.txt"
            val liste = fileAsList(filnavn)
            val kravlinjer = FileParser(liste).parseKravLinjer()

            TestContainer.dataSource.connection.use { con ->
                con.insertAllNewKrav(kravlinjer, filnavn)
                val lagredeKrav = con.getAllKrav()
                lagredeKrav.size shouldBe kravlinjer.size + 1
                lagredeKrav.filter { it.kravtype == NYTT_KRAV }.size shouldBe 8
                lagredeKrav.filter { it.kravtype == STOPP_KRAV }.size shouldBe 1
                lagredeKrav.filter { it.kravtype == ENDRING_RENTE }.size shouldBe 1
                lagredeKrav.filter { it.kravtype == ENDRING_HOVEDSTOL }.size shouldBe 1
            }
        }

        test("insertErrorMessage skal lagre feilmelding") {
            TestContainer.loadInitScript("Feilmeldinger.sql")
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

            TestContainer.dataSource.connection.use { con ->
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

        test("insertValidationError skal lagre valideringsfeil") {
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

            TestContainer.dataSource.connection.use { con ->
                val rsBefore = con.prepareStatement("""select count(*) from valideringsfeil""").executeQuery()
                rsBefore.next()
                rsBefore.getInt("count") shouldBe 0

                con.insertValidationError(fileName, linje, feilMelding)

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
    })*/