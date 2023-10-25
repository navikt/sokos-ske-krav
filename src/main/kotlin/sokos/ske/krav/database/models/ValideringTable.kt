package sokos.ske.krav.database.models

import java.time.LocalDateTime

data class ValideringTable(
	val valideringID: Long,
	val saksnummerSKE: String,
	val jsondataSKE: String,
	val dato: LocalDateTime,
) {
	override fun toString(): String {
		return "ValideringTable(validering_id=$valideringID, kravidentifikator_ske='$saksnummerSKE', jsondata_ske='$jsondataSKE', dato=$dato)"
	}
}

