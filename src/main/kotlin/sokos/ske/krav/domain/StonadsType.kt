package sokos.ske.krav.domain

import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje

enum class StonadsType(val kravKode: String, val hjemmelkode: String, val alternativHjemmelkode: String =""){
    TILBAKEKREVING_BARNETRYGD                               ("BA OR", "T"),
    TILBAKEKREVING_OMSORGSPENGER                            ("BS OM", "T"),
    TILBAKEKREVING_PLEIEPENGER_BARN                         ("BS PN", "T"),
    TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE                 ("BS PP", "T"),
    TILBAKEKREVING_STOENAD_TIL_BARNETILSYN                  ("EF BT", "T"),
    TILBAKEKREVING_OVERGANGSSTOENAD                         ("EF OG", "T"),
    TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL               ("FA FE", "T"),
    TILBAKEKREVING_FORELDREPENGER                           ("FA FØ", "T"),
    TILBAKEKREVING_SVANGERSKAPSPENGER                       ("FA SV", "T"),
    TILBAKEKREVING_FORSKUTTERTE_DAGPENGER                   ("FO FT", "FT"),
    TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS          ("FR SN", "T"),
    TILBAKEKREVING_SYKEPENGER                               ("KT SP", "T"),
    TILBAKEKREVING_PERMITTERINGSPENGER_KORONA               ("LK LK", "T"),
    TILBAKEKREVING_LOENNSKOMPENSASJON                       ("LK RF", "T"),
    TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR        ("PE AF", "T"),
    TILBAKEKREVING_ALDERSPENSJON                            ("PE AP", "T"),
    TILBAKEKREVING_BARNEPENSJON                             ("PE BP", "T"),
    TILBAKEKREVING_GJENLEVENDE_PENSJON                      ("PE GP", "T"),
    TILBAKEKREVING_GJENLEVENDE_PENSJON_AVREGNING            ("PE GP", "TA"),
    TILBAKEKREVING_KRIGSPENSJON                             ("PE KP", "T"),
    TILBAKEKREVING_UFOEREPENSJON                            ("PE UP", "T"),
    TILBAKEKREVING_UFOEREPENSJON_UTBETALT_TIL_FEIL_MOTTAKER ("PE UP", "C"),
    TILBAKEKREVING_UFOERETRYGD                              ("PE UT", "T"),
    TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER                ("PE UT", "EU"),
    TILBAKEKREVING_UFOERETRYGD_UTBETALT_TIL_FEIL_MOTTAKER   ("PE UT", "C"),
    TILBAKEKREVING_UFOERETRYGD_AVREGNING                    ("PE UT", "TA"),
    TILBAKEKREVING_AVTALEFESTET_PENSJON                     ("PE XP", "T"),
    TILBAKEKREVING_SUPPLERENDE_STOENAD_ALDERSPENSJON        ("SU AP", "T") ,
    TILBAKEKREVING_SUPPLERENDE_STOENAD_UFOEREPENSJON        ("SU UF", "T") ,
    TILBAKEKREVING_OPPLAERINGSPENGER                        ("BS OP", "T") ,
    TILBAKEKREVING_UTDANNINGSSTOENAD                        ("EF UT", "T"),
    TILBAKEKREVING_KONTANTSTOETTE                           ("KS KS", "T"),
    TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON          ("PE FP", "T"),
    TILBAKEKREVING_GAMMEL_YRKESSKADEPENSJON                 ("PE GY", "T"),
    TILBAKEKREVING_OMSTILLINGSSTOENAD                       ("OM OM", "T")
    ;

   companion object {
        fun getStonadstype(
           krav: KravTable
        ): StonadsType {
           return StonadsType.entries.firstOrNull {
               krav.kravkode == it.kravKode  &&  (krav.kodeHjemmel == it.hjemmelkode  || krav.kodeHjemmel == it.alternativHjemmelkode) }
               ?: throw NotImplementedError(
               "Kombinasjonen kravkode=${krav.kravkode} og hjemmelkode=${krav.kodeHjemmel} gir ingen kravtype.",
           )

        }
        fun getStonadstype(
           krav: KravLinje
        ): StonadsType {
            return StonadsType.entries.firstOrNull {
               krav.kravKode == it.kravKode  &&  (krav.kodeHjemmel == it.hjemmelkode  || krav.kodeHjemmel == it.alternativHjemmelkode) }
               ?: throw NotImplementedError(
               "Kombinasjonen kravkode=${krav.kravKode} og hjemmelkode=${krav.kodeHjemmel} gir ingen kravtype.",
           )
        }
    }
}
