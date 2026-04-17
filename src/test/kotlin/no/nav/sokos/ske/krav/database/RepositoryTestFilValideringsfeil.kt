package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.time.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.util.DBUtils.transaction

internal class RepositoryTestFilValideringsfeil :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/validering/FilValideringsFeil.sql")
        }

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            DBListener.dataSource.transaction { tx ->
                FilValideringsfeilRepository.getFilValideringsFeilForFil(tx, "Fil1.txt").size shouldBe 1
                FilValideringsfeilRepository.getFilValideringsFeilForFil(tx, "Fil2.txt").size shouldBe 2
                FilValideringsfeilRepository.getFilValideringsFeilForFil(tx, "Fil3.txt").size shouldBe 3
            }
        }
        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            DBListener.dataSource.transaction { tx ->
                FilValideringsfeilRepository.insertFileValideringsfeil(tx, "Fil4.txt", "Test validation error insert")

                val inserted = FilValideringsfeilRepository.getFilValideringsFeilForFil(tx, "Fil4.txt")
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

            val feilMelding = "Test validation error insert med non-null kravlinje"
            val fileName = "Non-null test"

            val valideringsFeilBefore =
                DBListener.dataSource.transaction { tx ->
                    tx.list(queryOf("select * from filvalideringsfeil"), FilValideringsfeilRepository.mapToFilValideringsfeil)
                }

            DBListener.dataSource.transaction { tx ->
                FilValideringsfeilRepository.insertLineFilValideringsfeil(tx, fileName, linje, feilMelding)
            }

            val valideringsFeil =
                DBListener.dataSource.transaction { tx ->
                    tx.list(queryOf("select * from filvalideringsfeil"), FilValideringsfeilRepository.mapToFilValideringsfeil)
                }
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

        test("getValideringsFeilForLinje skal returnere en liste av ValideringsFeil knyttet til gitt filnavn og linjenummer") {
            DBListener.dataSource.transaction { tx ->
                with(FilValideringsfeilRepository.getFilValideringsFeilForLinje(tx, "Fil1.txt", 1)) {
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

                with(FilValideringsfeilRepository.getFilValideringsFeilForLinje(tx, "Fil2.txt", 2)) {
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

                with(FilValideringsfeilRepository.getFilValideringsFeilForLinje(tx, "Fil3.txt", 3)) {
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

        test("deleteOldFilValideringsFeil skal slette alle filvalideringsfeil som ble opprettet før en spesifisert tid") {
            DBListener.dataSource.transaction { tx ->
                val threshold = LocalDate.parse("2023-01-02")
                val filValideringsfeilDeleted = FilValideringsfeilRepository.deleteOldFilValideringsfeil(tx, threshold)
                filValideringsfeilDeleted shouldBe 2
            }
        }

        afterTest {
            DBListener.clearDB()
        }
    })
