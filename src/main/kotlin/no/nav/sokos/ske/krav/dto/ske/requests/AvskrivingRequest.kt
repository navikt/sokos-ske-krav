package no.nav.sokos.ske.krav.dto.ske.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvskrivingRequest(
    @SerialName("kravidentifikatortype")
    val kravidentifikatorType: String,
    val kravidentifikator: String,
)
