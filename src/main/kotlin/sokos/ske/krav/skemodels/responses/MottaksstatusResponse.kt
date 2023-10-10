package sokos.ske.krav.skemodels.responses

import kotlinx.serialization.Serializable

@Serializable
data class MottaksstatusResponse(

    val kravidentifikator: String,
    val oppdragsgiversKravidentifikator: String,
    val mottaksstatus: String,
    val statusOppdatert: String
){
    enum class Mottaksstatus(val value: String){
        MOTTATTUNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
        VALIDERINGSFEIL("VALIDERINGSFEIL"),
        RESKONTROFOERT("RESKONTROFOERT");

        companion object {
            private val map = Mottaksstatus.values().associateBy { it.value }
            infix fun from(value: String) = map[value]
        }
    }

    override fun toString(): String {
        return "MottaksstatusResponse(kravidentifikator='$kravidentifikator', mottaksstatus='$mottaksstatus', oppdragsgiversKravidentifikator=$oppdragsgiversKravidentifikator, statusOppdatert=$statusOppdatert)"
    }


}