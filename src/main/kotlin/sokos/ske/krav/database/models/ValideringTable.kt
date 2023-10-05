package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ValideringTable(
    val validering_id:  Long,
    val saksnummer_ske: String,
    val jsondata_ske:   String,
    val dato:           LocalDateTime,
){
    override fun toString(): String {
        return "ValideringTable(validering_id=$validering_id, saksnummer_ske='$saksnummer_ske', jsondata_ske='$jsondata_ske', dato=$dato)"
    }
}

