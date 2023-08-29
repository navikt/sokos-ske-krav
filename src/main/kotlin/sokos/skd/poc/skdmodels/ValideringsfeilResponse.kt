package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsfeilResponse (

    val valideringsfeil: Array<ValideringsfeilDTO>
) {
}