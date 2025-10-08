package no.nav.sokos.ske.krav.database

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository.getAllFeilmeldinger
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository.getFeilmeldingForKravId
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository.insertFeilmelding

internal class RepositoryTestFeilmelding :
    FunSpec({
        val dbListener = DBListener()
        dbListener.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
            dbListener.dataSource.connection.use { it.getAllFeilmeldinger().size shouldBe 4 }
        }

        test("getFeilmeldingForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            dbListener.dataSource.connection.use { con ->
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

            dbListener.dataSource.connection.use { con ->
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
