package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class YtelseForAvregningBeloep (

    val valuta: Valuta,
    val beloep: kotlin.Long
) {
    enum class Valuta(val value: kotlin.String){
        NOK("NOK");
    }
}