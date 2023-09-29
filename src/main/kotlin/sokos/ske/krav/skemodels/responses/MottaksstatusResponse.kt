package sokos.ske.krav.skemodels.responses

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

//
//    "kravidentifikator": "string",
//    "oppdragsgiversKravidentifikator": "string",
//    "mottaksstatus": "MOTTATT_UNDER_BEHANDLING",
//    "statusOppdatert": "2023-09-29T21:10:39.896Z"

@Serializable
data class MottaksstatusResponse(

    val kravidentifikator: String,
    val oppdragsgiversKravidentifikator: Mottaksstatus,
    val mottaksstatus: String,
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