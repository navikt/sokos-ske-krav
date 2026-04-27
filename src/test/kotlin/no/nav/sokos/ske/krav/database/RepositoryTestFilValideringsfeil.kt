package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository

internal class RepositoryTestFilValideringsfeil :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/validering/FilValideringsFeil.sql")
        }

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            with(filvalideringsFeilRepository) {
                getFilValideringsFeilForFil("Fil1.txt").shouldHaveSize(1)
                getFilValideringsFeilForFil("Fil2.txt").shouldHaveSize(2)
                getFilValideringsFeilForFil("Fil3.txt").shouldHaveSize(3)
            }
        }

        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            filvalideringsFeilRepository.insertFilValideringsfeil("Fil4.txt", "Test validation error insert")
            val insertedErrors = filvalideringsFeilRepository.getFilValideringsFeilForFil("Fil4.txt")

            insertedErrors.size shouldBe 1
            insertedErrors.first().run {
                filnavn shouldBe "Fil4.txt"
                linjenummer shouldBe 0
                saksnummerNav shouldBe ""
                kravLinje shouldBe ""
                feilmelding shouldBe "Test validation error insert"
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

            val feilMelding = "Test validation error insert med non-null kravlinje"
            val filename = "Non-null test"
            filvalideringsFeilRepository.insertLineFilValideringsfeil(filename, linje, feilMelding)
            val allInsertedFiles = filvalideringsFeilRepository.getAllValideringsFeil()

            allInsertedFiles.size shouldBe 7

            val insertedFilesForFilename = allInsertedFiles.filter { it.filnavn == filename }
            insertedFilesForFilename.size shouldBe 1
            with(insertedFilesForFilename.first()) {
                linjenummer shouldBe linje.linjenummer
                linjenummer shouldBe linje.linjenummer
                saksnummerNav shouldBe linje.saksnummerNav
                kravLinje shouldBe linje.toString()
                feilmelding shouldBe feilMelding
            }
        }

        test("deleteOldFilValideringsFeil skal slette alle filvalideringsfeil som ble opprettet før en spesifisert tid") {
            val threshold = LocalDate.parse("2023-01-02")
            val filValideringsfeilDeleted = filvalideringsFeilRepository.deleteOldFilValideringsfeil(threshold)
            filValideringsfeilDeleted shouldBe 2
        }

        afterTest {
            DBListener.clearDB()
        }
    })

private fun FilValideringsfeilRepository.getAllValideringsFeil(): List<FilValideringsfeil> =
    transaction { session ->
        session.list(
            queryOf("select * from filvalideringsfeil"),
            mapToFilValideringsfeil,
        )
    }
