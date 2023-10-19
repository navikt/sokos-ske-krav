package sokos.ske.krav.skemodels.responses

import kotlinx.serialization.Serializable

@Serializable
data class SokosValideringsfeil (

    val kravidSke: String,
    val valideringsfeilResponse: ValideringsfeilResponse,

)