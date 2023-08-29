package sokos.skd.poc.skdmodels

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class RenteBeloep (

    val valuta: Valuta,
    val beloep: Long,
    val renterIlagtDato: LocalDate
) {
    enum class Valuta(val value: String){
        NOK("NOK");
    }
}