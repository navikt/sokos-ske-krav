package sokos.ske.krav.util

enum class NAVKravtypeMapping(
    val stoenadstype: StoenadstypeKodeNAV,
    val hjemmelkode: HjemmelkodePak,
    val kravtype: Kravtype,
) {
    BA_OR_T(StoenadstypeKodeNAV.BA_OR, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_BARNETRYGD),
    BS_OM_T(StoenadstypeKodeNAV.BS_OM, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_OMSORGSPENGER),
    BS_PN_T(StoenadstypeKodeNAV.BS_PN, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_PLEIEPENGER_BARN),
    BS_PP_T(StoenadstypeKodeNAV.BS_PP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE),
    EF_BT_T(StoenadstypeKodeNAV.EF_BT, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_STOENAD_TIL_BARNETILSYN),
    EF_OG_T(StoenadstypeKodeNAV.EF_OG, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_OVERGANGSSTOENAD),
    FA_FE_T(StoenadstypeKodeNAV.FA_FE, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL),
    FA_FØ_T(StoenadstypeKodeNAV.FA_FØ, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_FORELDREPENGER),
    FA_SV_T(StoenadstypeKodeNAV.FA_SV, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_SVANGERSKAPSPENGER),
    FO_FT_FT(StoenadstypeKodeNAV.FO_FT, HjemmelkodePak.FT, Kravtype.TILBAKEKREVING_FORSKUTTERTE_DAGPENGER),
    FR_SN_T(StoenadstypeKodeNAV.FR_SN, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS),
    KT_SP_T(StoenadstypeKodeNAV.KT_SP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_SYKEPENGER),
    LK_LK_T(StoenadstypeKodeNAV.LK_LK, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_PERMITTERINGSPENGER_KORONA),
    LK_RF_T(StoenadstypeKodeNAV.LK_RF, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_LOENNSKOMPENSASJON),
    PE_AF_T(StoenadstypeKodeNAV.PE_AF, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR),
    PE_AP_T(StoenadstypeKodeNAV.PE_AP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_ALDERSPENSJON),
    PE_BP_T(StoenadstypeKodeNAV.PE_BP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_BARNEPENSJON),
    PE_GP_T(StoenadstypeKodeNAV.PE_GP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_GJENLEVENDE_PENSJON),
    PE_GP_TA(StoenadstypeKodeNAV.PE_GP, HjemmelkodePak.TA, Kravtype.TILBAKEKREVING_GJENLEVENDE_PENSJON),
    PE_KP_T(StoenadstypeKodeNAV.PE_KP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_KRIGSPENSJON),
    PE_UP_T(StoenadstypeKodeNAV.PE_UP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_UFOEREPENSJON),
    PE_UT_T(StoenadstypeKodeNAV.PE_UT, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_UFOERETRYGD),
    PE_UT_EU(StoenadstypeKodeNAV.PE_UT, HjemmelkodePak.EU, Kravtype.TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER),
    PE_XP_T(StoenadstypeKodeNAV.PE_XP, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_AVTALEFESTET_PENSJON),
    SU_UF_T(StoenadstypeKodeNAV.SU_UF, HjemmelkodePak.T, Kravtype.TILBAKEKREVING_SUPPLERENDE_STOENAD),
    ;

    companion object {
        fun getKravtype(
            stoenadstype: StoenadstypeKodeNAV,
            hjemmelkode: HjemmelkodePak,
        ): Kravtype {
            return entries.find {
                it.stoenadstype == stoenadstype && it.hjemmelkode == hjemmelkode
            }?.kravtype ?: throw NotImplementedError(
                "Kombinasjonen stoenadstype=$stoenadstype og hjemmelkode=$hjemmelkode gir ingen kravtype.",
            )
        }
    }
}

enum class StoenadstypeKodeNAV(val value: String) {
    BA_OR("BA OR"),
    BS_OM("BS OM"),
    BS_PN("BS PN"),
    BS_PP("BS PP"),
    EF_BT("EF BT"),
    EF_OG("EF OG"),
    FA_FE("FA FE"),
    FA_FØ("FA FØ"),
    FA_SV("FA SV"),
    FO_FT("FO FT"),
    FR_SN("FR SN"),
    KT_SP("KT SP"),
    LK_LK("LK LK"),
    LK_RF("LK RF"),
    PE_AF("PE AF"),
    PE_AP("PE AP"),
    PE_BP("PE BP"),
    PE_GP("PE GP"),
    PE_KP("PE KP"),
    PE_UP("PE UP"),
    PE_UT("PE UT"),
    PE_XP("PE XP"),
    SU_UF("SU UF"),
    ;

    companion object {
        fun fromString(stoenadstypeKode: String): StoenadstypeKodeNAV {
            return entries.firstOrNull { it.value == stoenadstypeKode }
                ?: throw NotImplementedError("$stoenadstypeKode er ikke en gyldig stoenadstype")
        }
    }
}

enum class HjemmelkodePak {
    T,
    TA,
    FT,
    EU,
}

enum class Kravtype {
    // NAV
    TILBAKEKREVING_BARNETRYGD,
    TILBAKEKREVING_OMSORGSPENGER,
    TILBAKEKREVING_PLEIEPENGER_BARN,
    TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE,
    TILBAKEKREVING_STOENAD_TIL_BARNETILSYN,
    TILBAKEKREVING_OVERGANGSSTOENAD,
    TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL,
    TILBAKEKREVING_FORELDREPENGER,
    TILBAKEKREVING_SVANGERSKAPSPENGER,
    TILBAKEKREVING_FORSKUTTERTE_DAGPENGER,
    TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS,
    TILBAKEKREVING_SYKEPENGER,
    TILBAKEKREVING_PERMITTERINGSPENGER_KORONA,
    TILBAKEKREVING_LOENNSKOMPENSASJON,
    TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR,
    TILBAKEKREVING_ALDERSPENSJON,
    TILBAKEKREVING_BARNEPENSJON,
    TILBAKEKREVING_GJENLEVENDE_PENSJON,
    TILBAKEKREVING_KRIGSPENSJON,
    TILBAKEKREVING_UFOEREPENSJON,
    TILBAKEKREVING_UFOERETRYGD,
    TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER,
    TILBAKEKREVING_AVTALEFESTET_PENSJON,
    TILBAKEKREVING_SUPPLERENDE_STOENAD,
}
