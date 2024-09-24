package sokos.ske.krav.domain.ske.requests

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sokos.ske.krav.domain.StonadsType

@Serializable
@SerialName("opprettInnkrevingsoppdrag")
data class OpprettInnkrevingsoppdragRequest(
    @SerialName("kravtype")
    val stonadstype: StonadsType,
    val skyldner: Skyldner,
    val hovedstol: HovedstolBeloep,
    val renteBeloep: List<RenteBeloep>?,
    val oppdragsgiversReferanse: String,
    @SerialName("oppdragsgiversKravidentifikator")
    val oppdragsgiversKravIdentifikator: String,
    @SerialName("fastsettelsesdato")
    val fastsettelsesDato: LocalDate,
    @SerialName("foreldelsesfristensUtgangspunkt")
    val foreldelsesFristensUtgangspunkt: LocalDate? = null,
    @SerialName("tilleggsinformasjon")
    val tilleggsInformasjon: TilleggsinformasjonNav? = null,
)

@Serializable
data class RenteBeloep(
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long,
    val renterIlagtDato: LocalDate,
    val rentetype: String = "STRAFFERENTE",
)

@Serializable
data class Skyldner(
    @SerialName("identifikatortype")
    val identifikatorType: IdentifikatorType,
    val identifikator: String,
) {
    enum class IdentifikatorType(val value: String) {
        PERSON("PERSON"),
        ORGANISASJON("ORGANISASJON"),
    }
}

@Serializable
data class YtelseForAvregningBeloep(
    val beloep: Long,
    val valuta: Valuta = Valuta.NOK,
)

@Serializable
data class TilbakeKrevingsPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

@Serializable
data class TilleggsinformasjonNav(
    val ytelserForAvregning: YtelseForAvregningBeloep? = null,
    @SerialName("tilbakekrevingsperiode")
    val tilbakeKrevingsPeriode: TilbakeKrevingsPeriode,
)

@Serializable
enum class Valuta(val value: String) {
    NOK("NOK"),
}
