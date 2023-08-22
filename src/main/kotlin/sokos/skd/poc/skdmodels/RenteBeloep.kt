package sokos.skd.poc.skdmodels


data class RenteBeloep (

    val valuta: Valuta,
    val beloep: kotlin.Long,
    val renterIlagtDato: java.time.LocalDate
) {
    enum class Valuta(val value: kotlin.String){
        NOK("NOK");
    }
}