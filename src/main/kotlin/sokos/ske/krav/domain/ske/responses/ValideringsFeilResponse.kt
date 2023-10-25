package sokos.ske.krav.domain.ske.responses

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsFeilResponse(
	val valideringsfeil: List<ValideringsFeil>,
)

@Serializable
data class ValideringsFeil(
	val error: String,
	val message: String,
)
