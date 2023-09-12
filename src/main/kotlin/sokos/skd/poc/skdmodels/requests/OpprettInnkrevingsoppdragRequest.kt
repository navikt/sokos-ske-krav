package sokos.skd.poc.skdmodels.requests

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("opprettInnkrevingsoppdrag")
data class OpprettInnkrevingsoppdragRequest(

    val kravtype: String,
    val skyldner: Skyldner,
    val hovedstol: HovedstolBeloep,
    val renteBeloep: Array<RenteBeloep>?,
    val oppdragsgiversSaksnummer: String,
    val oppdragsgiversKravidentifikator: String,
    val fastsettelsesdato: LocalDate,
    val foreldelsesfristensUtgangspunkt: LocalDate? = null,
    val tilleggsinformasjon: TilleggsinformasjonNav? = null,
):SkeRequest {
    enum class Kravtype(val value: String){
        TILBAKEKREVINGFEILUTBETALTYTELSE("TILBAKEKREVING_FEILUTBETALT_YTELSE"),
        FORSIKRINGSPREMIESELVSTENDIGNAERINGSDRIVENDE("FORSIKRINGSPREMIE_SELVSTENDIG_NAERINGSDRIVENDE");
    }
}

@Serializable
data class RenteBeloep (
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long,
    val renterIlagtDato: LocalDate
)

@Serializable
data class Skyldner (
    val identifikatortype: Identifikatortype,
    val identifikator: String
) {
    enum class Identifikatortype(val value: String){
        PERSON("PERSON"),
        ORGANISASJON("ORGANISASJON");
    }
}

@Serializable
data class YtelseForAvregningBeloep (
    val beloep: Long,
    val valuta: Valuta = Valuta.NOK,
)

@Serializable
data class TilleggsinformasjonNav (
    val stoenadstype: String,
    val ytelserForAvregning: YtelseForAvregningBeloep? = null
) {
    enum class Stonadstype(val value: String){
        FORELDREPENGER("FA FØ"),
        KOMPENSASJON_INNTEKSTAP_FRILANSER_OG_NAERING("FR SN"),
        OMSORG_OPPLAERING_OG_PLEIEPENGER("KT OOP"),
        AVTALEFESTET_PENSJON("PE XP"),
        AVTALEFESTET_PENSJON_PRIVATSEKTOR("PE AF"),
        ALDERSPENSJON("PE AP"),
        BARNEPENSJON("PE BP"),
        TIDLIGERE_FAMILIEPLEIER_PENSJON("PE FP"),
        GJENLEVENDEPENSJON("PE GP"),
        GAMMEL_YRKESSKADEPENSJON("PE GY"),
        KRIGSPENSJON("PE KP"),
        UFOEREPENSJON("PE UP"),
        REFUSSJON_UTGIFTSDEKNING("FA FE"),
        SYKEPENGER("KT SP"),
        SVANGERSKAPSPENGER("FA SV"),
        UFOERETRYGD("PE UT"),
        FORSKUDD_TILBAKEKREVING("FO FT"),
        PERMITERINGSPENGER_KORONA("LK LK"),
        LOENSKOMP_ARBEIDSGIVER_PERMITERTE("LK RF"),
        BARNETRYGD("BA OR"),
        OPPLAERINGSPENGER("BS OP"),
        OMSORGSPENGER("BS OM"),
        PLEIEPENGER_BARN("BS PN"),
        PLEIEPENGER_NAERSTAAENDE("BS PP"),
        ENSLIG_FORSOERGER_OVERGANGSSTOENAD("EF OG"),
        ENSLIG_FORSOERGER_BARNETILSYN("EF BT"),
        ENSLIG_FORSOERGER_SKOLEPENGER("EF UT"),
        SUPPLERENDE_STOENAD_UFOERE("SU UF"),
        KONTANTSTOETTE("KS KS"),
        DAGPENGER("DAGPENGER");

        companion object {
            private val map = Stonadstype.values().associateBy { it.value }
            infix fun from(value: String) = map[value]
        }
    }
}
