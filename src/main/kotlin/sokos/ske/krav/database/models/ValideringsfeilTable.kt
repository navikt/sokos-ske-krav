package sokos.ske.krav.database.models

import sokos.ske.krav.domain.nav.KravLinje
import java.time.LocalDateTime


data class ValideringsfeilTable(
    val valideringsfeilId: Long,
    val filnavn: String,
    val linjenummer: Int,
    val saksnummer: String,
    val kravLinje: String,
    val feilmelding: String,
    val datoOpprettet: LocalDateTime,
)

