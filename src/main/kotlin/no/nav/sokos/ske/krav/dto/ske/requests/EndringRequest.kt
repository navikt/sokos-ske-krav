package no.nav.sokos.ske.krav.dto.ske.requests

import kotlinx.serialization.Serializable

@Serializable
data class NyHovedStolRequest(
    val hovedstol: HovedstolBeloep,
)

@Serializable
data class EndreRenteBeloepRequest(
    val renter: List<RenteBeloep>,
)
