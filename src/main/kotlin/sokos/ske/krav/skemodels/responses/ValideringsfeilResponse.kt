package sokos.ske.krav.skemodels.responses

import kotlinx.serialization.Serializable
import sokos.ske.krav.skemodels.validering.ValideringsfeilDTO

@Serializable
data class ValideringsfeilResponse (
    val valideringsfeil: List<ValideringsfeilDTO>
)