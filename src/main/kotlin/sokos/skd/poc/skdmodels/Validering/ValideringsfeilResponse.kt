package sokos.skd.poc.skdmodels.Validering

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsfeilResponse (

    val valideringsfeil: Array<ValideringsfeilDTO>
) {
}