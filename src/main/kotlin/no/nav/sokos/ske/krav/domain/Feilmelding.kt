package no.nav.sokos.ske.krav.domain

import java.time.LocalDateTime

data class Feilmelding(
    val feilmeldingId: Long,
    val kravId: Long,
    val corrId: String,
    val saksnummerNav: String,
    val kravidentifikatorSKE: String?,
    val error: String,
    val melding: String,
    val navRequest: String,
    val skeResponse: String,
    val tidspunktOpprettet: LocalDateTime,
    val rapporter: Boolean = true,
)
