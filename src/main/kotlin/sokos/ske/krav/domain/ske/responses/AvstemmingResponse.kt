package sokos.ske.krav.domain.ske.responses

import kotlinx.serialization.Serializable

@Serializable
data class AvstemmingResponse(
    val kravidentifikator: String,
)
