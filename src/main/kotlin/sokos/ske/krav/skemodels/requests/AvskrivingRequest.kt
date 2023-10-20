package sokos.ske.krav.skemodels.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest (
    @SerialName("kravidentifikatortype")
    val kravidentifikatorType: String = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
    val kravidentifikator: String
)