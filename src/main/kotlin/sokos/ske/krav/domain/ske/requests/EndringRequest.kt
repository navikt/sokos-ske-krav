package sokos.ske.krav.domain.ske.requests

import kotlinx.serialization.Serializable

@Serializable
data class EndreHovedStolRequest(
    val nyHovedstol: HovedstolBeloep,
)

@Serializable
data class EndreRenterRequest(
    val renter: List<RenteBeloep>,
)
