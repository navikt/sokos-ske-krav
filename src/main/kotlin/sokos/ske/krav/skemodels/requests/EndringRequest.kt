package sokos.ske.krav.skemodels.requests

import kotlinx.serialization.Serializable

@Serializable
data class EndringRequest (

    val kravidentifikatortype: String = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
    val kravidentifikator: String,
    val nyHovedstol: HovedstolBeloep
):SkeRequest {
    enum class Kravidentifikatortype(val value: String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}