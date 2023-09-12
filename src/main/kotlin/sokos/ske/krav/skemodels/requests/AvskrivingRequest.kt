package sokos.ske.krav.skemodels.requests

import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest (

    val kravidentifikatortype: String = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
    val kravidentifikator: String
) {
    enum class Kravidentifikatortype(val value: String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}