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

@Serializable
data class FeilResponse(
  val type: String,
  val title: String,
  val status: Int,
  val detail: String,
  val instance: String,
)