package sokos.ske.krav.service.unit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.domain.nav.KravLinje

internal class StonadsTypeTest :
    FunSpec({

        // TODO: Bruk Junie
        test("getStonadstype should return correct StonadsType for all KravTable combinations") {
            val kravMap =
                mapOf(
                    Pair("BA OR", "T") to StonadsType.TILBAKEKREVING_BARNETRYGD,
            /*        Pair("EN OR", "E") to StonadsType.TILBAKEKREVING_ENSLIG_FORSORGER,
                    Pair("SU PV", "S") to StonadsType.TILBAKEKREVING_SUPPLERENDE,
                    Pair("AA OR", "A") to StonadsType.TILBAKEKREVING_ARBEIDSAVKLARING,*/
                )

            kravMap.forEach { (input, expected) ->
                val krav =
                    mockk<KravTable> {
                        every { kravkode } returns input.first
                        every { kodeHjemmel } returns input.second
                    }
                val stonadsType = StonadsType.getStonadstype(krav)
                stonadsType shouldBe expected
            }
        }

        test("getStonadstype should throw NotImplementedError for unknown KravTable") {
            val krav =
                mockk<KravTable> {
                    every { kravkode } returns "UNKNOWN"
                    every { kodeHjemmel } returns "UNKNOWN"
                }
            shouldThrow<NotImplementedError> {
                StonadsType.getStonadstype(krav)
            }
        }

        test("getStonadstype should throw NotImplementedError for unknown KravLinje") {
            val krav =
                mockk<KravLinje> {
                    every { kravKode } returns "UNKNOWN"
                    every { kodeHjemmel } returns "UNKNOWN"
                }
            shouldThrow<NotImplementedError> {
                StonadsType.getStonadstype(krav)
            }
        }
    })
