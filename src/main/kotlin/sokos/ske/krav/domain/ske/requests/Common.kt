package sokos.ske.krav.domain.ske.requests

import kotlinx.serialization.Serializable

@Serializable
data class HovedstolBeloep(
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long
)

enum class Kravidentifikatortype(val value: String) {
	SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
}