package no.nav.sokos.ske.krav.dto.ske.responses

import kotlinx.serialization.Serializable

@Serializable
data class OpprettInnkrevingsOppdragResponse(
    val kravidentifikator: String,
)
