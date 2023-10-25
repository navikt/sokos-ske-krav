package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class FeilmeldingTable(
    val feilmeldingId: Long,
    val kravId: Long,
    val saksnummer: String,
    val kravidentifikatorSKE: String,
    val error: String,
    val melding: String,
    val navRequest: String,
    val skeResponse: String,
    val dato: LocalDateTime,
) {
}

