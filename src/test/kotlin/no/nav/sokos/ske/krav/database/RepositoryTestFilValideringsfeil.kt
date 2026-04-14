package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.deleteOldFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertFileValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertLineFilValideringsfeil
import no.nav.sokos.ske.krav.repository.toValideringsfeil

internal class RepositoryTestFilValideringsfeil :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/validering/FilValideringsFeil.sql")
        }

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            DBListener.dataSource.connection.use { con ->
                con.getFilValideringsFeilForFil("Fil1.txt").size shouldBe 1
                con.getFilValideringsFeilForFil("Fil2.txt").size shouldBe 2
                con.getFilValideringsFeilForFil("Fil3.txt").size shouldBe 3
            }
        }
        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            DBListener.dataSource.connection.use { con ->
                con.insertFileValideringsfeil("Fil4.txt", "Test validation error insert")

                val inserted = con.getFilValideringsFeilForFil("Fil4.txt")
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
                    null,
                    Avsender.OB04.name,
                )

            DBListener.dataSource.connection.use { con ->
                val feilMelding = "Test validation error insert med non-null kravlinje"
                val fileName = "Non-null test"

                val valideringsFeilBefore = con.prepareStatement("""select * from filvalideringsfeil""").executeQuery().toValideringsfeil()

                con.insertLineFilValideringsfeil(fileName, linje, feilMelding)

                val valideringsFeil = con.prepareStatement("""select * from filvalideringsfeil""").executeQuery().toValideringsfeil()
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

        test("deleteOldFilValideringsFeil skal slette alle filvalideringsfeil som ble opprettet før en spesifisert tid") {
            DBListener.dataSource.connection.use { con ->
                val threshold = LocalDate.parse("2023-01-02")
                val filValideringsfeilDeleted = con.deleteOldFilValideringsfeil(threshold)

                filValideringsfeilDeleted shouldBe 2
            }
        }

        afterTest {
            DBListener.clearDB()
        }
    })
