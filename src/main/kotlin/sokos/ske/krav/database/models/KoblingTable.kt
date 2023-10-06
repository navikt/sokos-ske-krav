package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class KoblingTable(
    val id:  Long,
    val saksref_fil: String,
    val saksref_uuid:   String,
    val dato:           LocalDateTime,
){
    override fun toString(): String {
        return "KoblingTable(id=$id, saksref_fil='$saksref_fil', saksref_uuid='$saksref_uuid', dato=$dato)"
    }
}

