package sokos.skd.poc.skdmodels

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class OpprettInnkrevingsoppdragRequest(

    val kravtype: String,
    val skyldner: Skyldner,
    val hovedstol: HovedstolBeloep,
    val renteBeloep: Array<RenteBeloep>?,
    val oppdragsgiversSaksnummer: String,
    val oppdragsgiversKravidentifikator: String,
    val fastsettelsesdato: LocalDate,
    val foreldelsesfristensUtgangspunkt: LocalDate? = null,
    val tilleggsinformasjon: TilleggsinformasjonNav? = null
) {
    enum class Kravtype(val value: String){
        TILBAKEKREVINGFEILUTBETALTYTELSE("TILBAKEKREVING_FEILUTBETALT_YTELSE"),
        FORSIKRINGSPREMIESELVSTENDIGNAERINGSDRIVENDE("FORSIKRINGSPREMIE_SELVSTENDIG_NAERINGSDRIVENDE");
    }
}