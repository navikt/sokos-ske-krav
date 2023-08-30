package sokos.skd.poc.skdmodels.Endring

import kotlinx.serialization.Serializable
import sokos.skd.poc.skdmodels.NyttOppdrag.HovedstolBeloep

@Serializable
data class EndringRequest (

    val kravidentifikatortype: String,
    val kravidentifikator: String,
    val nyHovedstol: HovedstolBeloep
) {
    enum class Kravidentifikatortype(val value: String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}