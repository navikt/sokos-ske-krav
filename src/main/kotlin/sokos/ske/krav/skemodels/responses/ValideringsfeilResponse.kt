package sokos.ske.krav.skemodels.responses

import kotlinx.serialization.Serializable
import sokos.ske.krav.skemodels.Validering.ValideringsfeilDTO

@Serializable
data class ValideringsfeilResponse (

    val valideringsfeil: Array<ValideringsfeilDTO>
) {
}