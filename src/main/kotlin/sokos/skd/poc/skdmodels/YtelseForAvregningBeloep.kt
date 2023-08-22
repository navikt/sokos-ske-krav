package sokos.skd.poc.skdmodels


data class YtelseForAvregningBeloep (

    val valuta: Valuta,
    val beloep: kotlin.Long
) {
    enum class Valuta(val value: kotlin.String){
        NOK("NOK");
    }
}