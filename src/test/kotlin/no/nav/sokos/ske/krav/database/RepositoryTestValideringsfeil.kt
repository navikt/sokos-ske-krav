package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.getValideringsFeilForFil
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.getValideringsFeilForLinje
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.insertFileValideringsfeil
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.insertLineValideringsfeil
import no.nav.sokos.ske.krav.database.repository.toValideringsfeil
import no.nav.sokos.ske.krav.domain.nav.KravLinje
import no.nav.sokos.ske.krav.util.TestContainer

internal class RepositoryTestValideringsfeil :
    FunSpec({
        val testContainer = TestContainer()
        testContainer.migrate("SQLscript/ValideringsFeil.sql")

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            testContainer.dataSource.connection.use { con ->
                con.getValideringsFeilForFil("Fil1.txt").size shouldBe 1
                con.getValideringsFeilForFil("Fil2.txt").size shouldBe 2
                con.getValideringsFeilForFil("Fil3.txt").size shouldBe 3
            }
        }
        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            testContainer.dataSource.connection.use { con ->
                con.insertFileValideringsfeil("Fil4.txt", "Test validation error insert")

                val inserted = con.getValideringsFeilForFil("Fil4.txt")
                inserted.size shouldBe 1
                inserted.first().run {
                    filnavn shouldBe "Fil4.txt"
                    linjenummer shouldBe 0
                    saksnummerNav shouldBe ""
                    kravLinje shouldBe ""
                    feilmelding shouldBe "Test validation error insert"
                }
            }
        }

        test("insertLineValideringsfeil skal inserte ny valideringsfeil med filnanvn, linjenummer, saksnummerNav, kravlinje, og feilmelding") {
            val linje =
                KravLinje(
                    55,
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

            testContainer.dataSource.connection.use { con ->
                val feilMelding = "Test validation error insert med non-null kravlinje"
                val fileName = "Non-null test"

                val valideringsFeilBefore = con.prepareStatement("""select * from valideringsfeil""").executeQuery().toValideringsfeil()

                con.insertLineValideringsfeil(fileName, linje, feilMelding)

                val valideringsFeil = con.prepareStatement("""select * from valideringsfeil""").executeQuery().toValideringsfeil()
                valideringsFeil.size shouldBe valideringsFeilBefore.size + 1
                valideringsFeil.filter { it.filnavn == fileName }.run {
                    size shouldBe 1
                    with(first()) {
                        linjenummer shouldBe linje.linjenummer
                        saksnummerNav shouldBe linje.saksnummerNav
                        kravLinje shouldBe linje.toString()
                        feilmelding shouldBe feilMelding
                    }
                }
            }
        }

        test("getValideringsFeilForLinje skal returnere en liste av ValideringsFeil knyttet til gitt filnavn og linjenummer") {

            testContainer.dataSource.connection.use { con ->
                with(con.getValideringsFeilForLinje("Fil1.txt", 1)) {
                    size shouldBe 1
                    with(first()) {
                        valideringsfeilId shouldBe 11
                        filnavn shouldBe "Fil1.txt"
                        linjenummer shouldBe 1
                        saksnummerNav shouldBe "111"
                        kravLinje shouldBe "linje1"
                        feilmelding shouldBe "feilmelding1"
                    }
                }
            }

            testContainer.dataSource.connection.use { con ->
                with(con.getValideringsFeilForLinje("Fil2.txt", 2)) {
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
                with(con.getValideringsFeilForLinje("Fil3.txt", 3)) {
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
