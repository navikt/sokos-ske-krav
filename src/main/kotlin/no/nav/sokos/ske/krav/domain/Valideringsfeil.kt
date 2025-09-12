package no.nav.sokos.ske.krav.domain

import java.time.LocalDateTime

data class Valideringsfeil(
    val valideringsfeilId: Long,
    val filnavn: String,
    val linjenummer: Int,
    val saksnummerNav: String,
    val kravLinje: String,
    val feilmelding: String,
    val tidspunktOpprettet: LocalDateTime,
    val rapporter: Boolean,
)
