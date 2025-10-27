package no.nav.sokos.ske.krav.dto.ske.requests

import kotlinx.serialization.Serializable

@Serializable
data class HovedstolBeloep(
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long,
)

enum class KravidentifikatorType(
    val value: String,
) {
    SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR"),
    OPPDRAGSGIVERSKRAVIDENTIFIKATOR("OPPDRAGSGIVERS_KRAVIDENTIFIKATOR"),
}
