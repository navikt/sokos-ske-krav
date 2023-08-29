package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class EndringRequest (

    val kravidentifikatortype: String,
    val kravidentifikator: String,
    val nyHovedstol: HovedstolBeloep
) {
    enum class Kravidentifikatortype(val value: kotlin.String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}