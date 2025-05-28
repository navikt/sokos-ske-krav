package no.nav.sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import no.nav.sokos.ske.krav.database.models.FeilmeldingTable
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.getAllFeilmeldinger
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.getFeilmeldingForKravId
import no.nav.sokos.ske.krav.database.repository.FeilmeldingRepository.insertFeilmelding
import no.nav.sokos.ske.krav.util.TestContainer

internal class RepositoryTestFeilmelding :
    FunSpec({
        val testContainer = TestContainer()
        testContainer.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
            testContainer.dataSource.connection.use { it.getAllFeilmeldinger().size shouldBe 4 }
        }

        test("getFeilmeldingForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            testContainer.dataSource.connection.use { con ->
                val feilmelding1 = con.getFeilmeldingForKravId(1)
                feilmelding1.size shouldBe 1
                feilmelding1.first().corrId shouldBe "CORR856"

                val feilmelding2 = con.getFeilmeldingForKravId(2)
                feilmelding2.size shouldBe 2
                feilmelding2.filter { it.error == "404" }.size shouldBe 2
                feilmelding2.map { it.corrId shouldBe "CORR658" }

                val feilmelding3 = con.getFeilmeldingForKravId(3)
                feilmelding3.size shouldBe 1
                feilmelding3.filter { it.error == "500" }.size shouldBe 1
                feilmelding3.first().corrId shouldBe "CORR457389"
            }
        }

        test("insertFeilmelding skal lagre feilmelding") {
            val feilmelding =
                FeilmeldingTable(
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

            testContainer.dataSource.connection.use { con ->
                val feilmeldingerBefore = con.getAllFeilmeldinger()
                con.insertFeilmelding(feilmelding)

                val feilmeldinger = con.getAllFeilmeldinger()
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
