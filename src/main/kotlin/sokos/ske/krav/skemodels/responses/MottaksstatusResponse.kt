package sokos.ske.krav.skemodels.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MottaksstatusResponse(

    val kravidentifikator: String,
    val oppdragsgiversKravidentifikator: String,
    @SerialName("mottaksstatus")
    val mottaksStatus: String,
    val statusOppdatert: String
){
    enum class MottaksStatus(val value: String){
        MOTTATTUNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
        VALIDERINGSFEIL("VALIDERINGSFEIL"),
        RESKONTROFOERT("RESKONTROFOERT");

        companion object {
            private val map = MottaksStatus.values().associateBy { it.value }
            infix fun from(value: String) = map[value]
        }
    }

    override fun toString(): String {
        return "MottaksstatusResponse(kravidentifikator='$kravidentifikator', mottaksstatus='$mottaksStatus', oppdragsgiversKravidentifikator=$oppdragsgiversKravidentifikator, statusOppdatert=$statusOppdatert)"
    }


}