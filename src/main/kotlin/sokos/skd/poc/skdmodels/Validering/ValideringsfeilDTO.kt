package sokos.skd.poc.skdmodels.Validering

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsfeilDTO (

    val error: String,
    val message: String
) {
}