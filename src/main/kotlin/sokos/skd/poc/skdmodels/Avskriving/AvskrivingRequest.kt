package sokos.skd.poc.skdmodels.Avskriving

import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest (

    val kravidentifikatortype: String,
    val kravidentifikator: String
) {
    enum class Kravidentifikatortype(val value: String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}