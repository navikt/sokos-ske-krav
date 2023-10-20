package sokos.ske.krav.skemodels.validering

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsfeilDTO (

    val error: String,
    val message: String
)