package sokos.skd.poc.skdmodels.responses

import kotlinx.serialization.Serializable
import sokos.skd.poc.skdmodels.Validering.ValideringsfeilDTO

@Serializable
data class ValideringsfeilResponse (

    val valideringsfeil: Array<ValideringsfeilDTO>
) {
}