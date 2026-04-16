package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.deleteOldFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertFilValideringsfeil
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.insertLineFilValideringsfeil
import no.nav.sokos.ske.krav.util.DBUtils.transaction

internal class RepositoryTestFilValideringsfeil :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/validering/FilValideringsFeil.sql")
        }

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            val (forFile1, forFile2, forFile3) =
                DBListener.dataSource.transaction { tx ->
                    listOf(
                        getFilValideringsFeilForFil(tx, "Fil1.txt"),
                        getFilValideringsFeilForFil(tx, "Fil2.txt"),
                        getFilValideringsFeilForFil(tx, "Fil3.txt"),
                    )
                }

            forFile1.shouldHaveSize(1)
            forFile2.shouldHaveSize(2)
            forFile3.shouldHaveSize(3)
        }

        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            val insertedErrors =
                DBListener.dataSource.transaction { tx ->
                    insertFilValideringsfeil(tx, "Fil4.txt", "Test validation error insert")
                    getFilValideringsFeilForFil(tx, "Fil4.txt")
                }

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
            val allInsertedFiles =
                DBListener.dataSource.transaction { tx ->
                    insertLineFilValideringsfeil(tx, filename, linje, feilMelding)
                    FilValideringsfeilRepository.getAllValideringsFeil(tx)
                }

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
            val filValideringsfeilDeleted =
                DBListener.dataSource.transaction { tx ->
                    deleteOldFilValideringsfeil(tx, threshold)
                }

            filValideringsfeilDeleted shouldBe 2
        }

        afterTest {
            DBListener.clearDB()
        }
    })

private fun FilValideringsfeilRepository.getAllValideringsFeil(tx: TransactionalSession): List<FilValideringsfeil> =
    tx.list(
        queryOf("select * from filvalideringsfeil"),
        mapToFilValideringsfeil,
    )
