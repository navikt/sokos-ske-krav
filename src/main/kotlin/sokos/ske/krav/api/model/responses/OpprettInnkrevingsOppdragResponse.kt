package sokos.ske.krav.api.model.responses

import kotlinx.serialization.Serializable

@Serializable
data class OpprettInnkrevingsOppdragResponse(
	val kravidentifikator: String
)