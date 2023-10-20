package sokos.ske.krav.api.model.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EndringRequest (
    @SerialName("kravidentifikatortype")
    val kravidentifikatorType: String = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
    val kravidentifikator: String,
    val nyHovedstol: HovedstolBeloep
)