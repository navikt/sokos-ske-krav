package sokos.skd.poc.skdmodels.NyttOppdrag

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
@Serializable
enum class Valuta(val value: String){
    NOK("NOK");
}
@Serializable
data class HovedstolBeloep (
    val valuta: Valuta,
    val beloep: Long
)
@Serializable
data class RenteBeloep (
    val valuta: Valuta,
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