package sokos.skd.poc.skdmodels

data class OpprettInnkrevingsoppdragRequest (

    val kravtype: String,
    val skyldner: Skyldner,
    val hovedstol: HovedstolBeloep,
    val renteBeloep: Array<RenteBeloep>?,
    val oppdragsgiversSaksnummer: String,
    val oppdragsgiversKravidentifikator: String,
    val fastsettelsesdato: java.time.LocalDate,
    val foreldelsesfristensUtgangspunkt: java.time.LocalDate? = null,
    val tilleggsinformasjon: TilleggsinformasjonNav? = null
) {
    enum class Kravtype(val value: String){
        TILBAKEKREVINGFEILUTBETALTYTELSE("TILBAKEKREVING_FEILUTBETALT_YTELSE"),
        FORSIKRINGSPREMIESELVSTENDIGNAERINGSDRIVENDE("FORSIKRINGSPREMIE_SELVSTENDIG_NAERINGSDRIVENDE");
    }
}