package sokos.ske.krav.skemodels.requests

import kotlinx.serialization.Serializable

@Serializable
enum class Valuta(val value: String){
    NOK("NOK");
}
@Serializable
data class HovedstolBeloep (
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long
)