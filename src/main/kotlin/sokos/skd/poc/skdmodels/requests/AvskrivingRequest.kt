package sokos.skd.poc.skdmodels.requests

import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest (

    val kravidentifikatortype: String = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
    val kravidentifikator: String
):SkeRequest {
    enum class Kravidentifikatortype(val value: String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}