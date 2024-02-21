package sokos.ske.krav.database.models

import java.time.LocalDateTime


data class FeilmeldingTable(
    val feilmeldingId: Long,
    val kravId: Long,
    val corrId: String,
    val saksnummer: String,
    val kravidentifikatorSKE: String,
    val error: String,
    val melding: String,
    val navRequest: String,
    val skeResponse: String,
    val dato: LocalDateTime,
)

