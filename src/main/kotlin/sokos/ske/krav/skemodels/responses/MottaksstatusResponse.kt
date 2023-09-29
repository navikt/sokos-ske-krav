package sokos.ske.krav.skemodels.responses

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class MottaksstatusResponse(
    val kravidentifikator: String,
    val mottaksstatus: String,
    val oppdragsgiversKravidentifikator: Mottaksstatus,
    val statusOppdatert: LocalDateTime
){
    enum class Mottaksstatus(val value: kotlin.String){
        MOTTATTUNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
        VALIDERINGSFEIL("VALIDERINGSFEIL"),
        RESKONTROFOERT("RESKONTROFOERT");
    }

    override fun toString(): String {
        return "MottaksstatusResponse(kravidentifikator='$kravidentifikator', mottaksstatus='$mottaksstatus', oppdragsgiversKravidentifikator=$oppdragsgiversKravidentifikator, statusOppdatert=$statusOppdatert)"
    }


}