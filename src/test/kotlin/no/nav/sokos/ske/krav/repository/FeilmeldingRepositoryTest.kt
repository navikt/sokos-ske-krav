package no.nav.sokos.ske.krav.repository

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.feilmeldingRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction

class FeilmeldingRepositoryTest :
    FunSpec({
        extensions(PostgresListener)

        PostgresListener.migrate("SQLscript/Feilmeldinger.sql")

        test("getAllFeilmeldinger skal returnere alle feilmeldinger ") {
            feilmeldingRepository.getAllFeilmeldinger().size shouldBe 4
        }

        test("getFeilmeldingForKravId skal returnere en liste med feilmeldinger for angitt kravid") {
            val feilmelding1 = feilmeldingRepository.getFeilmeldingForKravId(1)
            feilmelding1.size shouldBe 1
            feilmelding1.first().corrId shouldBe "CORR856"

            val feilmelding2 = feilmeldingRepository.getFeilmeldingForKravId(2)
            feilmelding2.size shouldBe 2
            feilmelding2.filter { it.error == "404" }.size shouldBe 2
            feilmelding2.map { it.corrId shouldBe "CORR658" }

            val feilmelding3 = feilmeldingRepository.getFeilmeldingForKravId(3)
            feilmelding3.size shouldBe 1
            feilmelding3.filter { it.error == "500" }.size shouldBe 1
            feilmelding3.first().corrId shouldBe "CORR457389"
        }

        test("insertFeilmelding skal lagre feilmelding") {
            val feilmelding =
                Feilmelding(
                    kravId = 999L,
                    corrId = "CORR456",
                    saksnummerNav = "1110-navsaksnummer",
                    kravidentifikatorSKE = "1111-skeUUID",
                    error = "409",
                    melding = "feilmelding 409 1111",
                    navRequest = "{nav request2}",
                    skeResponse = "{ske response 2}",
                    tidspunktOpprettet = LocalDateTime.now(),
                    rapporter = false,
                )

            val feilmeldingerBefore = feilmeldingRepository.getAllFeilmeldinger()
            PostgresListener.dataSource.transaction { session ->
                feilmeldingRepository.insertFeilmeldinger(listOf(feilmelding), session)
            }

            val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
            feilmeldinger.size shouldBe 1 + feilmeldingerBefore.size
            with(feilmeldinger.filter { it.corrId == feilmelding.corrId }) {
                size shouldBe 1
                with(first()) {
                    kravId shouldBe feilmelding.kravId
                    saksnummerNav shouldBe feilmelding.saksnummerNav
                }
            }
        }
    })
