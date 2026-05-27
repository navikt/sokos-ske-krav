package no.nav.sokos.ske.krav.dto.ske.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import no.nav.sokos.ske.krav.domain.Status

@Serializable
data class MottaksStatusResponse(
    val kravidentifikator: String,
    val oppdragsgiversKravidentifikator: String,
    @SerialName("mottaksstatus")
    val mottaksStatus: Status,
    val statusOppdatert: String,
)
