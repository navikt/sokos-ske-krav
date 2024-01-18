package sokos.ske.krav.domain.ske.requests

import kotlinx.serialization.Serializable

@Serializable
data class NyHovedStolRequest(
    val hovedstol: HovedstolBeloep,
)

@Serializable
data class EndreRenteBeloepRequest(
    val renter: List<RenteBeloep>,
)

@Serializable
data class NyOppdragsgiversReferanseRequest(
    val nyOppdragsgiversReferanse: String
)