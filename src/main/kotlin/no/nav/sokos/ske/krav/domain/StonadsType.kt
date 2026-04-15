package no.nav.sokos.ske.krav.domain

enum class StonadsType(
    vararg val identifikatorer: Identifikator,
) {
    TILBAKEKREVING_ALDERSPENSJON(Identifikator("PE AP", "T")),
    TILBAKEKREVING_ALDERSPENSJON_AVREGNING(Identifikator("PE AP", "TA")),
    TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER(
        Identifikator("AAP AAP", "T"),
        Identifikator("AE AA", "AT"),
    ),
    TILBAKEKREVING_ATTFOERINGSPENGER(Identifikator("AE AP", "AT")),
    TILBAKEKREVING_ATTFOERINGSYTELSER(Identifikator("AE AY", "AT")),
    TILBAKEKREVING_AVTALEFESTET_PENSJON(Identifikator("PE XP", "T")),
    TILBAKEKREVING_AVTALEFESTET_PENSJON_ETTEROPPGJOER(Identifikator("PE XP", "A")),
    TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR(Identifikator("PE AF", "T")),
    TILBAKEKREVING_BARNETRYGD(Identifikator("BA OR", "T")),
    TILBAKEKREVING_BARNEPENSJON(Identifikator("PE BP", "T")),
    TILBAKEKREVING_BEHOVSPROEVET_GRAVFERDSHJELP(Identifikator("GS GB", "T")),
    TILBAKEKREVING_DAGPENGER(
        Identifikator("DP DP", "T"),
        Identifikator("AE DP", "AT"),
    ),
    TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL(Identifikator("FA FE", "T")),
    TILBAKEKREVING_FORELDREPENGER(Identifikator("FA FØ", "T")),
    TILBAKEKREVING_FORSKUTTERTE_DAGPENGER(Identifikator("FO FT", "FT")),
    TILBAKEKREVING_GAMMEL_YRKESSKADEPENSJON(Identifikator("PE GY", "T")),
    TILBAKEKREVING_GJENLEVENDE_PENSJON(Identifikator("PE GP", "T")),
    TILBAKEKREVING_GJENLEVENDE_PENSJON_AVREGNING(Identifikator("PE GP", "TA")),
    TILBAKEKREVING_GRAVFERDSSTOENAD(Identifikator("GS GD", "T")),
    TILBAKEKREVING_GRUNNSTOENAD(Identifikator("GH GS", "T")),
    TILBAKEKREVING_HJELPESTOENAD(Identifikator("GH HS", "T")),
    TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS(Identifikator("FR SN", "T")),
    TILBAKEKREVING_KONTANTSTOETTE(Identifikator("KS KS", "T")),
    TILBAKEKREVING_KONTANTSTOETTE_UTLAND(Identifikator("KS EU", "T")),
    TILBAKEKREVING_KRIGSPENSJON(Identifikator("PE KP", "T")),
    TILBAKEKREVING_LOENNSKOMPENSASJON(Identifikator("LK RF", "T")),
    TILBAKEKREVING_MENERSTATNING_YRKESSKADE(Identifikator("YS YE", "T")),
    TILBAKEKREVING_MENERSTATNING_YRKESSYKDOM(Identifikator("YS YS", "T")),
    TILBAKEKREVING_MOBILITETSFREMMENDE_STOENADER(Identifikator("AE MS", "AT")),
    TILBAKEKREVING_OMSORGSPENGER(Identifikator("BS OM", "T")),
    TILBAKEKREVING_OMSTILLINGSSTOENAD(Identifikator("OM OM", "T")),
    TILBAKEKREVING_OMSTILLINGSSTOENAD_ETTEROPPGJOER(Identifikator("OM OM", "EO")),
    TILBAKEKREVING_OPPLAERINGSPENGER(Identifikator("BS OP", "T")),
    TILBAKEKREVING_OVERGANGSSTOENAD(Identifikator("EF OG", "T")),
    TILBAKEKREVING_PERMITTERINGSPENGER_KORONA(Identifikator("LK LK", "T")),
    TILBAKEKREVING_PLEIEPENGER_BARN(Identifikator("BS PN", "T"), Identifikator("BS PB", "T")),
    TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE(Identifikator("BS PP", "T")),
    TILBAKEKREVING_SPESIALUTBETALING(Identifikator("AE SU", "AT")),
    TILBAKEKREVING_STOENAD_BAARETRANSPORT(Identifikator("GS BT", "T")),
    TILBAKEKREVING_STOENAD_MOPEDBIL(Identifikator("HJ MP", "GX")),
    TILBAKEKREVING_STOENAD_MOTORKJOERETOEY_GR1(Identifikator("HJ M1", "GX")),
    TILBAKEKREVING_STOENAD_MOTORKJOERETOEY_GR2(Identifikator("HJ M2", "GX")),
    TILBAKEKREVING_STOENAD_ORTOPEDISKE_HJELPEMIDLER(Identifikator("HT OH", "T")),
    TILBAKEKREVING_STOENAD_SPESIALUTSTYR_BIL(Identifikator("HJ MU", "GX")),
    TILBAKEKREVING_STOENAD_TIL_BARNETILSYN(Identifikator("EF BT", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_ALDERSPENSJON(Identifikator("SU AP", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_EKTEFELLE_OVER_67(Identifikator("SU EO", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_EKTEFELLE_UNDER_67(Identifikator("SU EU", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_ENSLIG_MINDREAARIG(Identifikator("SU EN", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_ENSLIG_VOKSEN(Identifikator("SU EV", "T")),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_UFOEREPENSJON(Identifikator("SU UF", "T")),
    TILBAKEKREVING_SVANGERSKAPSPENGER(Identifikator("FA SV", "T")),
    TILBAKEKREVING_SYKEPENGER(Identifikator("KT SP", "T"), Identifikator("SP SP", "T")),
    TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON(Identifikator("PE FP", "T")),
    TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON_AVREGNING(Identifikator("PE FP", "TA")),
    TILBAKEKREVING_TILLEGGSTOENAD(
        Identifikator("TS TS", "T"),
        Identifikator("AE TA", "AT"),
        Identifikator("AE TT", "AT"),
    ),
    TILBAKEKREVING_TILLEGGSTOENADER(Identifikator("AE TS", "AT")),
    TILBAKEKREVING_TILSKUDD_RIMELIGE_HJELPEMIDLER(Identifikator("HJ DA", "T")),
    TILBAKEKREVING_TILTAKSPENGER(
        Identifikator("TP TP", "T"),
        Identifikator("AE IS", "AT"),
    ),
    TILBAKEKREVING_UFOEREPENSJON(Identifikator("PE UP", "T")),
    TILBAKEKREVING_UFOEREPENSJON_AVREGNING(Identifikator("PE UP", "TA")),
    TILBAKEKREVING_UFOERETRYGD(Identifikator("PE UT", "T")),
    TILBAKEKREVING_UFOERETRYGD_AVREGNING(Identifikator("PE UT", "TA")),
    TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER(Identifikator("PE UT", "EU")),
    TILBAKEKREVING_UNGDOMSPROGRAMYTELSEN(Identifikator("UNG", "T")),
    TILBAKEKREVING_UTDANNINGSSTOENAD(Identifikator("EF UT", "T")),
    TILBAKEKREVING_UTVIDET_BARNETRYGD(Identifikator("BA UT", "T")),
    ;

    val kravKoder: List<String> get() = identifikatorer.map { it.kravKode }

    data class Identifikator(
        val kravKode: String,
        val kodeHjemmel: String,
    )

    companion object {
        fun getStonadstype(
            kravkode: String,
            kodeHjemmel: String,
        ): StonadsType =
            StonadsType.entries.firstOrNull { type ->
                type.identifikatorer.any { it.kravKode == kravkode && it.kodeHjemmel == kodeHjemmel }
            }
                ?: throw NotImplementedError(
                    "Kombinasjonen kravkode=$kravkode og hjemmelkode=$kodeHjemmel gir ingen stønadstype.",
                )
    }
}
