package sokos.ske.krav.api.model.responses

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
