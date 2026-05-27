package no.nav.sokos.ske.krav.database

import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.util.transaction

internal class RepositoryTestFeilmelding :
    FunSpec({
        extensions(DBListener)

        beforeTest {
            DBListener.loadInitScripts("SQLscript/feilmeldinger/Feilmeldinger.sql")
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
            feilmelding2.forAll { it.corrId shouldBe "CORR658" }

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

            dataSource.transaction { session ->
                feilmeldingRepository.insertFeilmelding(session, feilmelding)

                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger(session)
                feilmeldinger.shouldHaveSize(1)
                with(feilmeldinger.first()) {
                    kravId shouldBe feilmelding.kravId
                    saksnummerNav shouldBe feilmelding.saksnummerNav
                }
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

            dataSource.transaction { session ->
                feilmeldingRepository.insertFeilmeldinger(session, feilmeldinger)

                val savedFeilmeldinger = feilmeldingRepository.getAllFeilmeldinger(session)
                savedFeilmeldinger.shouldHaveSize(2)
                with(savedFeilmeldinger.first()) {
                    kravId shouldBe 0L
                    saksnummerNav shouldBe "1110-navsaksnummer"
                }
            }
        }

        test("updateStatusForAvstemtKravToReported skal sette rapporter til false på krav med angitt kravid") {
            dataSource.transaction { session ->
                feilmeldingRepository.updateStatusForAvstemtKravToReported(session, 1)
                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger(session)
                feilmeldinger.filter { !it.rapporter }.shouldHaveSize(1)
            }
        }

        test("deleteOldFeilmeldinger skal slette alle feilmeldingene som ble opprettet før en spesifisert tid") {
            dataSource.transaction { session ->
                val threshold = LocalDate.parse("2023-01-02")
                val feilmeldingDeleted = feilmeldingRepository.deleteOldFeilmeldinger(session, threshold)
                feilmeldingDeleted shouldBe 2
            }
        }

        afterTest {
            DBListener.clearDB()
        }
    })
