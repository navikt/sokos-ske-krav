package no.nav.sokos.ske.krav.database

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.util.DBUtils.transaction

internal class RepositoryTestFeilmelding :
    FunSpec({
        extensions(DBListener)

        DBListener.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
            FeilmeldingRepository.getAllFeilmeldinger(DBListener.session).size shouldBe 4
        }

        test("getFeilmeldingForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            val feilmelding1 = FeilmeldingRepository.getFeilmeldingForKravId(DBListener.session, 1)
            feilmelding1.size shouldBe 1
            feilmelding1.first().corrId shouldBe "CORR856"

            val feilmelding2 = FeilmeldingRepository.getFeilmeldingForKravId(DBListener.session, 2)
            feilmelding2.size shouldBe 2
            feilmelding2.filter { it.error == "404" }.size shouldBe 2
            feilmelding2.map { it.corrId shouldBe "CORR658" }

            val feilmelding3 = FeilmeldingRepository.getFeilmeldingForKravId(DBListener.session, 3)
            feilmelding3.size shouldBe 1
            feilmelding3.filter { it.error == "500" }.size shouldBe 1
            feilmelding3.first().corrId shouldBe "CORR457389"
        }

        test("insertFeilmelding skal lagre feilmelding") {
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

            DBListener.dataSource.transaction { session ->
                val feilmeldingerBefore = FeilmeldingRepository.getAllFeilmeldinger(session)
                FeilmeldingRepository.insertFeilmeldinger(session, listOf(feilmelding))

                val feilmeldinger = FeilmeldingRepository.getAllFeilmeldinger(session)
                feilmeldinger.size shouldBe 1 + feilmeldingerBefore.size
                with(feilmeldinger.filter { it.corrId == feilmelding.corrId }) {
                    size shouldBe 1
                    with(first()) {
                        kravId shouldBe feilmelding.kravId
                        saksnummerNav shouldBe feilmelding.saksnummerNav
                    }
                }
            }
        }
    })
