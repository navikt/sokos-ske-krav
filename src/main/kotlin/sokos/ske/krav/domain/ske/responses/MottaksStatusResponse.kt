package sokos.ske.krav.domain.ske.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MottaksStatusResponse(
	val kravidentifikator: String,
	val oppdragsgiversKravidentifikator: String,
	@SerialName("mottaksstatus")
	val mottaksStatus: String,
	val statusOppdatert: String
) {

  override fun toString(): String {
		return "MottaksstatusResponse(kravidentifikator='$kravidentifikator', mottaksstatus='$mottaksStatus', oppdragsgiversKravidentifikator=$oppdragsgiversKravidentifikator, statusOppdatert=$statusOppdatert)"
	}
}