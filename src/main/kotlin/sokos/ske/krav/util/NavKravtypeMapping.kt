package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.KravLinje

enum class KravTypeMappingFraNAVTilSKE(val stonadsKode: String, val hjemmelkode: String, val alternativHjemmelkode: String =""){
    TILBAKEKREVING_BARNETRYGD                       ("BA OR", "T"),
    TILBAKEKREVING_OMSORGSPENGER                    ("BS OM", "T"),
    TILBAKEKREVING_PLEIEPENGER_BARN                 ("BS PN", "T"),
    TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE         ("BS PP", "T"),
    TILBAKEKREVING_STOENAD_TIL_BARNETILSYN          ("EF BT", "T"),
    TILBAKEKREVING_OVERGANGSSTOENAD                 ("EF OG", "T"),
    TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL       ("FA FE", "T"),
    TILBAKEKREVING_FORELDREPENGER                   ("FA FØ", "T"),
    TILBAKEKREVING_SVANGERSKAPSPENGER               ("FA SV", "T"),
    TILBAKEKREVING_FORSKUTTERTE_DAGPENGER           ("FO FT", "FT"),
    TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS  ("FR SN", "T"),
    TILBAKEKREVING_SYKEPENGER                       ("KT SP", "T"),
    TILBAKEKREVING_PERMITTERINGSPENGER_KORONA       ("LK LK", "T"),
    TILBAKEKREVING_LOENNSKOMPENSASJON               ("LK RF", "T"),
    TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR("PE AF", "T"),
    TILBAKEKREVING_ALDERSPENSJON                    ("PE AP", "T"),
    TILBAKEKREVING_BARNEPENSJON                     ("PE BP", "T"),
    TILBAKEKREVING_GJENLEVENDE_PENSJON              ("PE GP", "T", "TA"),
    TILBAKEKREVING_KRIGSPENSJON                     ("PE KP", "T"),
    TILBAKEKREVING_UFOEREPENSJON                    ("PE UP", "T"),
    TILBAKEKREVING_UFOERETRYGD                      ("PE UT", "T"),
    TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER        ("PE UT", "EU"),
    TILBAKEKREVING_AVTALEFESTET_PENSJON             ("PE XP", "T"),
    TILBAKEKREVING_SUPPLERENDE_STOENAD              ("SU UF", "T")
    ;


   companion object {
        fun getKravtype(
           krav: KravLinje
        ): KravTypeMappingFraNAVTilSKE {
           return KravTypeMappingFraNAVTilSKE.entries.firstOrNull {
               krav.stonadsKode == it.stonadsKode  &&  (krav.hjemmelKode == it.hjemmelkode  || krav.hjemmelKode == it.alternativHjemmelkode) }
               ?: throw NotImplementedError(
               "Kombinasjonen stoenadstype=${krav.stonadsKode} og hjemmelkode=${krav.hjemmelKode} gir ingen kravtype.",
           )

        }
    }
}
