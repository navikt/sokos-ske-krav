package sokos.ske.krav.database.models

import java.time.LocalDateTime


data class ValideringsfeilTable(
    val valideringsfeilId: Long,
    val filnavn: String,
    val linjenummer: Int,
    val saksnummerNav: String,
    val kravLinje: String,
    val feilmelding: String,
    val tidspunktOpprettet: LocalDateTime,
)

