package no.nav.sokos.ske.krav.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.valideringsfeilRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction

class ValideringsfeilRepositoryTest :
    FunSpec({
        extensions(PostgresListener)

        PostgresListener.migrate("SQLscript/ValideringsFeil.sql")

        test("getValideringsFeilForFil skal returnere valideringsfeil basert på filnavn") {
            valideringsfeilRepository.getValideringsFeilForFil("Fil1.txt").size shouldBe 1
            valideringsfeilRepository.getValideringsFeilForFil("Fil2.txt").size shouldBe 2
            valideringsfeilRepository.getValideringsFeilForFil("Fil3.txt").size shouldBe 3
        }

        test("insertFileValideringsfeil skal inserte ny valideringsfeil med filnanvn og feilmelding") {
            PostgresListener.dataSource.transaction { session ->
                valideringsfeilRepository.insertFileValideringsfeil("Fil4.txt", "Test validation error insert", session)
            }
            val inserted = valideringsfeilRepository.getValideringsFeilForFil("Fil4.txt")
            inserted.size shouldBe 1
            inserted.first().run {
                filnavn shouldBe "Fil4.txt"
                linjenummer shouldBe 0
                saksnummerNav shouldBe ""
                kravLinje shouldBe ""
                feilmelding shouldBe "Test validation error insert"
            }
        }

        test("getValideringsFeilForLinje skal returnere en liste av ValideringsFeil knyttet til gitt filnavn og linjenummer") {

            valideringsfeilRepository.getValideringsFeilForLinje("Fil1.txt", 1).run {
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

            valideringsfeilRepository.getValideringsFeilForLinje("Fil2.txt", 2).run {
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

            valideringsfeilRepository.getValideringsFeilForLinje("Fil3.txt", 3).run {
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
    })
