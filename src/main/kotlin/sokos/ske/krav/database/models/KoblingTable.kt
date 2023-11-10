package sokos.ske.krav.database.models

import java.time.LocalDateTime

data class KoblingTable(
    val id: Long,
    val saksrefFraFil: String,
    val saksrefUUID: String,
    val dato: LocalDateTime,
)