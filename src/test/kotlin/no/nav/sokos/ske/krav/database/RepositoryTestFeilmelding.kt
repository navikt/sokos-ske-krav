package no.nav.sokos.ske.krav.database

import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository

internal class RepositoryTestFeilmelding :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScript("SQLscript/feilmeldinger/Feilmeldinger.sql")
        }

        test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
            feilmeldingRepository.getAllFeilmeldinger().size shouldBe 4
        }

        test("getFeilmeldingForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            val feilmelding1 = feilmeldingRepository.getFeilmeldingerForKravId(1)
            feilmelding1.shouldHaveSize(1)
            feilmelding1.first().corrId shouldBe "CORR856"

            val feilmelding2 = feilmeldingRepository.getFeilmeldingerForKravId(2)
            feilmelding2.shouldHaveSize(2)
            feilmelding2.filter { it.error == "404" }.shouldHaveSize(2)
            feilmelding2.shouldForAll { it.corrId shouldBe "CORR658" }

            val feilmelding3 = feilmeldingRepository.getFeilmeldingerForKravId(3)
            feilmelding3.shouldHaveSize(1)
            feilmelding3.filter { it.error == "500" }.shouldHaveSize(1)
            feilmelding3.first().corrId shouldBe "CORR457389"
        }

        test("insertFeilmelding skal lagre én feilmelding") {
            DBListener.clearDB()

            val feilmelding =
                Feilmelding(
                    2L,
                    999L,
                    "CORR456",
                    "1110-navsaksnummer",
                    "1111-skeUUID",
                    "409",
                    "feilmelding 409 1111",
                    "{nav request2}",
                    "{ske response 2}",
                    LocalDateTime.now(),
                    false,
                )

            feilmeldingRepository.insertFeilmelding(feilmelding)

            val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
            feilmeldinger.shouldHaveSize(1)
            with(feilmeldinger.first()) {
                kravId shouldBe feilmelding.kravId
                saksnummerNav shouldBe feilmelding.saksnummerNav
            }
        }

        test("insertFeilmeldinger skal lagre alle feilmeldingene") {
            DBListener.clearDB()

            val feilmeldinger =
                List(2) {
                    Feilmelding(
                        it.toLong(),
                        it.toLong(),
                        "CORR456",
                        "111$it-navsaksnummer",
                        "1111-skeUUID",
                        "409",
                        "feilmelding 409 1111",
                        "{nav request2}",
                        "{ske response 2}",
                        LocalDateTime.now(),
                        false,
                    )
                }

            feilmeldingRepository.insertFeilmeldinger(feilmeldinger)

            val savedFeilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
            savedFeilmeldinger.shouldHaveSize(2)
            with(savedFeilmeldinger.first()) {
                kravId shouldBe 0L
                saksnummerNav shouldBe "1110-navsaksnummer"
            }
        }

        test("deleteOldFeilmeldinger skal slette alle feilmeldingene som ble opprettet før en spesifisert tid") {
            val threshold = LocalDate.parse("2023-01-02")
            val feilmeldingDeleted = feilmeldingRepository.deleteOldFeilmeldinger(threshold)
            feilmeldingDeleted shouldBe 2
        }

        afterTest {
            DBListener.clearDB()
        }
    })
