package sokos.ske.krav.database.models

import java.time.LocalDateTime

data class KravTable(
	val kravID: Long,
	val saksnummerNAV: String,
	val saksnummerSKE: String,
	val fildataNAV: String,
	val jsondataSKE: String,
	val status: String,
	val datoSendt: LocalDateTime,
	val datoSisteStatus: LocalDateTime,
	val kravtype: String
) {
	override fun toString(): String {
		return "KravTable(krav_id=$kravID, saksnummer_nav='$saksnummerNAV', saksnummer_ske='$saksnummerSKE', fildata_nav='$fildataNAV', jsondata_ske='$jsondataSKE', status='$status', dato_sendt=$datoSendt, dato_siste_status=$datoSisteStatus, kravtype='$kravtype')"
	}
}

