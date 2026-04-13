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
        val testCombinations =
            mapOf(
                Pair("AE AA", "AT") to StonadsType.TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER,
                Pair("AE AP", "AT") to StonadsType.TILBAKEKREVING_ATTFOERINGSPENGER,
                Pair("AE AY", "AT") to StonadsType.TILBAKEKREVING_ATTFOERINGSYTELSER,
                Pair("AE DP", "AT") to StonadsType.TILBAKEKREVING_DAGPENGER,
                Pair("AE IS", "AT") to StonadsType.TILBAKEKREVING_TILTAKSPENGER,
                Pair("AE MS", "AT") to StonadsType.TILBAKEKREVING_MOBILITETSFREMMENDE_STOENADER,
                Pair("AE SU", "AT") to StonadsType.TILBAKEKREVING_SPESIALUTBETALING,
                Pair("AE TA", "AT") to StonadsType.TILBAKEKREVING_TILLEGGSTOENAD,
                Pair("AE TT", "AT") to StonadsType.TILBAKEKREVING_TILLEGGSTOENAD,
                Pair("AE TS", "AT") to StonadsType.TILBAKEKREVING_TILLEGGSTOENADER,
                Pair("BA OR", "T") to StonadsType.TILBAKEKREVING_BARNETRYGD,
                Pair("BS OM", "T") to StonadsType.TILBAKEKREVING_OMSORGSPENGER,
                Pair("BS PN", "T") to StonadsType.TILBAKEKREVING_PLEIEPENGER_BARN,
                Pair("BS PB", "T") to StonadsType.TILBAKEKREVING_PLEIEPENGER_BARN,
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
                Pair("PE AP", "TA") to StonadsType.TILBAKEKREVING_ALDERSPENSJON_AVREGNING,
                Pair("PE BP", "T") to StonadsType.TILBAKEKREVING_BARNEPENSJON,
                Pair("PE FP", "TA") to StonadsType.TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON_AVREGNING,
                Pair("PE GP", "T") to StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON,
                Pair("PE GP", "TA") to StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON_AVREGNING,
                Pair("PE KP", "T") to StonadsType.TILBAKEKREVING_KRIGSPENSJON,
                Pair("PE UP", "T") to StonadsType.TILBAKEKREVING_UFOEREPENSJON,
                Pair("PE UT", "T") to StonadsType.TILBAKEKREVING_UFOERETRYGD,
                Pair("PE UT", "EU") to StonadsType.TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER,
                Pair("PE UT", "TA") to StonadsType.TILBAKEKREVING_UFOERETRYGD_AVREGNING,
                Pair("PE UP", "TA") to StonadsType.TILBAKEKREVING_UFOEREPENSJON_AVREGNING,
                Pair("PE XP", "T") to StonadsType.TILBAKEKREVING_AVTALEFESTET_PENSJON,
                Pair("PE XP", "AFP") to StonadsType.TILBAKEKREVING_AVTALEFESTET_PENSJON_ETTEROPPGJOER,
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
                Pair("GS GB", "T") to StonadsType.TILBAKEKREVING_BEHOVSPROEVET_GRAVFERDSHJELP,
                Pair("GS GD", "T") to StonadsType.TILBAKEKREVING_GRAVFERDSSTOENAD,
                Pair("GH GS", "T") to StonadsType.TILBAKEKREVING_GRUNNSTOENAD,
                Pair("GH HS", "T") to StonadsType.TILBAKEKREVING_HJELPESTOENAD,
                Pair("KS EU", "T") to StonadsType.TILBAKEKREVING_KONTANTSTOETTE_UTLAND,
                Pair("YS YE", "T") to StonadsType.TILBAKEKREVING_MENERSTATNING_YRKESSKADE,
                Pair("YS YS", "T") to StonadsType.TILBAKEKREVING_MENERSTATNING_YRKESSYKDOM,
                Pair("GS BT", "T") to StonadsType.TILBAKEKREVING_STOENAD_BAARETRANSPORT,
                Pair("HJ MP", "GX") to StonadsType.TILBAKEKREVING_STOENAD_MOPEDBIL,
                Pair("HJ M1", "GX") to StonadsType.TILBAKEKREVING_STOENAD_MOTORKJOERETOEY_GR1,
                Pair("HJ M2", "GX") to StonadsType.TILBAKEKREVING_STOENAD_MOTORKJOERETOEY_GR2,
                Pair("HT OH", "T") to StonadsType.TILBAKEKREVING_STOENAD_ORTOPEDISKE_HJELPEMIDLER,
                Pair("HJ MU", "GX") to StonadsType.TILBAKEKREVING_STOENAD_SPESIALUTSTYR_BIL,
                Pair("SU EO", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_EKTEFELLE_OVER_67,
                Pair("SU EU", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_EKTEFELLE_UNDER_67,
                Pair("SU EN", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_ENSLIG_MINDREAARIG,
                Pair("SU EV", "T") to StonadsType.TILBAKEKREVING_SUPPLERENDE_STOENAD_ENSLIG_VOKSEN,
                Pair("SP SP", "T") to StonadsType.TILBAKEKREVING_SYKEPENGER,
                Pair("HJ DA", "T") to StonadsType.TILBAKEKREVING_TILSKUDD_RIMELIGE_HJELPEMIDLER,
                Pair("BA UT", "T") to StonadsType.TILBAKEKREVING_UTVIDET_BARNETRYGD,
            )

        test("testdata skal dekke alle kravKode og kodeHjemmel kombinasjoner fra StonadsType") {
            val expectedCombinations =
                StonadsType.entries
                    .flatMap { stonadsType ->
                        stonadsType.identifikatorer.map { id ->
                            Pair(id.kravKode, id.kodeHjemmel)
                        }
                    }.toSet()

            val actualCombinations = testCombinations.keys.toSet()

            val missing = expectedCombinations - actualCombinations
            val extra = actualCombinations - expectedCombinations

            missing shouldBe emptySet()
            extra shouldBe emptySet()
        }

        test("getStonadstype skal returnere korrekt StonadsType for alle kombinasjoner") {
            testCombinations.forEach { (input, expected) ->
                val krav =
                    mockk<Krav> {
                        every { kravkode } returns input.first
                        every { kodeHjemmel } returns input.second
                    }
                val stonadsType = StonadsType.getStonadstype(krav.kravkode, krav.kodeHjemmel)
                stonadsType shouldBe expected
            }
        }

        test("getStonadstype skal kaste NotImplementedError for ukjent kombinasjon") {
            val krav =
                mockk<Krav> {
                    every { kravkode } returns "UNKNOWN"
                    every { kodeHjemmel } returns "UNKNOWN"
                }
            shouldThrow<NotImplementedError> {
                StonadsType.getStonadstype(krav.kravkode, krav.kodeHjemmel)
            }
        }

        test("kravKoder getter skal returnere alle kravkoder fra Identifikatorene") {
            val stonadsType = StonadsType.TILBAKEKREVING_TILLEGGSTOENAD

            val result = stonadsType.kravKoder

            result.size shouldBe 3
            result.toSet() shouldBe setOf("TS TS", "AE TA", "AE TT")
        }

        test("kravKoder getter skal returnere riktig kravkode når Identifikator inneholder kun 1") {
            val stonadsType = StonadsType.TILBAKEKREVING_UFOERETRYGD

            val result = stonadsType.kravKoder

            result.size shouldBe 1
            result shouldBe listOf("PE UT")
        }
    })
