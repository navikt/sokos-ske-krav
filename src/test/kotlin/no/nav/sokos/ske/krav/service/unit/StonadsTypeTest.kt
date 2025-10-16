package no.nav.sokos.ske.krav.service.unit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.StonadsType

internal class StonadsTypeTest :
    FunSpec({

        test("getStonadstype skal returnere korrekt StonadsType for alle kombinasjoner") {
            val stonadsTypeMap =
                mapOf(
                    Pair("BA OR", "T") to StonadsType.TILBAKEKREVING_BARNETRYGD,
                    Pair("BS OM", "T") to StonadsType.TILBAKEKREVING_OMSORGSPENGER,
                    Pair("BS PN", "T") to StonadsType.TILBAKEKREVING_PLEIEPENGER_BARN,
                    Pair("BS PP", "T") to StonadsType.TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE,
                    Pair("EF BT", "T") to StonadsType.TILBAKEKREVING_STOENAD_TIL_BARNETILSYN,
                    Pair("EF OG", "T") to StonadsType.TILBAKEKREVING_OVERGANGSSTOENAD,
                    Pair("FA FE", "T") to StonadsType.TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL,
                    Pair("FA FØ", "T") to StonadsType.TILBAKEKREVING_FORELDREPENGER,
                    Pair("FA SV", "T") to StonadsType.TILBAKEKREVING_SVANGERSKAPSPENGER,
                    Pair("FO FT", "FT") to StonadsType.TILBAKEKREVING_FORSKUTTERTE_DAGPENGER,
                    Pair("FR SN", "T") to StonadsType.TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS,
                    Pair("KT SP", "T") to StonadsType.TILBAKEKREVING_SYKEPENGER,
                    Pair("LK LK", "T") to StonadsType.TILBAKEKREVING_PERMITTERINGSPENGER_KORONA,
                    Pair("LK RF", "T") to StonadsType.TILBAKEKREVING_LOENNSKOMPENSASJON,
                    Pair("PE AF", "T") to StonadsType.TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR,
                    Pair("PE AP", "T") to StonadsType.TILBAKEKREVING_ALDERSPENSJON,
                    Pair("PE BP", "T") to StonadsType.TILBAKEKREVING_BARNEPENSJON,
                    Pair("PE GP", "T") to StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON,
                    Pair("PE GP", "TA") to StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON_AVREGNING,
                    Pair("PE KP", "T") to StonadsType.TILBAKEKREVING_KRIGSPENSJON,
                    Pair("PE UP", "T") to StonadsType.TILBAKEKREVING_UFOEREPENSJON,
                    Pair("PE UT", "T") to StonadsType.TILBAKEKREVING_UFOERETRYGD,
                    Pair("PE UT", "EU") to StonadsType.TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER,
                    Pair("PE UT", "TA") to StonadsType.TILBAKEKREVING_UFOERETRYGD_AVREGNING,
                    Pair("PE XP", "T") to StonadsType.TILBAKEKREVING_AVTALEFESTET_PENSJON,
                    Pair("SU AP", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_ALDERSPENSJON,
                    Pair("SU UF", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_UFOEREPENSJON,
                    Pair("BS OP", "T") to StonadsType.TILBAKEKREVING_OPPLAERINGSPENGER,
                    Pair("EF UT", "T") to StonadsType.TILBAKEKREVING_UTDANNINGSSTOENAD,
                    Pair("KS KS", "T") to StonadsType.TILBAKEKREVING_KONTANTSTOETTE,
                    Pair("PE FP", "T") to StonadsType.TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON,
                    Pair("PE GY", "T") to StonadsType.TILBAKEKREVING_GAMMEL_YRKESSKADEPENSJON,
                    Pair("OM OM", "T") to StonadsType.TILBAKEKREVING_OMSTILLINGSSTOENAD,
                    Pair("AAP AAP", "T") to StonadsType.TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER,
                    Pair("DP DP", "T") to StonadsType.TILBAKEKREVING_DAGPENGER,
                    Pair("TS TS", "T") to StonadsType.TILBAKEKREVING_TILLEGGSTOENAD,
                    Pair("TP TP", "T") to StonadsType.TILBAKEKREVING_TILTAKSPENGER,
                    Pair("UNG", "T") to StonadsType.TILBAKEKREVING_UNGDOMSPROGRAMYTELSEN,
                    Pair("OM OM", "EO") to StonadsType.TILBAKEKREVING_OMSTILLINGSSTOENAD_ETTEROPPGJOER,
                )

            stonadsTypeMap.size shouldBe StonadsType.entries.size

            stonadsTypeMap.forEach { (input, expected) ->
                val krav =
                    mockk<Krav> {
                        every { kravkode } returns input.first
                        every { kodeHjemmel } returns input.second
                    }
                val stonadsType = StonadsType.getStonadstype(krav.kravkode, krav.kodeHjemmel)
                stonadsType shouldBe expected
            }
        }

        test("getStonadstypes skal kaste NotImplementedError for ukjent kombinasjon") {
            val krav =
                mockk<Krav> {
                    every { kravkode } returns "UNKNOWN"
                    every { kodeHjemmel } returns "UNKNOWN"
                }
            shouldThrow<NotImplementedError> {
                StonadsType.getStonadstype(krav.kravkode, krav.kodeHjemmel)
            }
        }
    })
