package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest (

    val kravidentifikatortype: String,
    val kravidentifikator: String
) {
    enum class Kravidentifikatortype(val value: kotlin.String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}