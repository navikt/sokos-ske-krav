package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsfeilDTO (

    val error: String,
    val message: String
) {
}