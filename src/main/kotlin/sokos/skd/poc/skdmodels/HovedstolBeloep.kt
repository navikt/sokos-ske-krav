package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class HovedstolBeloep (

    val valuta: Valuta,
    val beloep: kotlin.Long
) {
    enum class Valuta(val value: String){
        NOK("NOK");
    }
}