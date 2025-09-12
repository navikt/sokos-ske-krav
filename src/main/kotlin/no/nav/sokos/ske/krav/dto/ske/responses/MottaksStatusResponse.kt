package no.nav.sokos.ske.krav.dto.ske.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MottaksStatusResponse(
    val kravidentifikator: String,
    val oppdragsgiversKravidentifikator: String,
    @SerialName("mottaksstatus")
    val mottaksStatus: String,
    val statusOppdatert: String,
)
