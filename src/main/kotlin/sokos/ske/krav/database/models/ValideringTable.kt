package sokos.ske.krav.database.models

import java.time.LocalDateTime

data class ValideringTable(
    val valideringID: Long,
    val saksnummerSKE: String,
    val jsondataSKE: String,
    val dato: LocalDateTime,
)
