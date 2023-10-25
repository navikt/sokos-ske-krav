package sokos.ske.krav.domain.ske.responses

import kotlinx.serialization.Serializable

@Serializable
data class OpprettInnkrevingsOppdragResponse(
	val kravidentifikator: String
)