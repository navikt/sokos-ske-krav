package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class KravTable(
    val krav_id: Long,
    val saksnummer_nav: String,
    val saksnummer_ske: String,
    val fildata_nav: String,
    val jsondata_ske: String,
    val status: String,
    val dato_sendt: LocalDateTime,
    val dato_siste_status: LocalDateTime,
    val kravtype: String
){
    override fun toString(): String {
        return "KravTable(krav_id=$krav_id, saksnummer_nav='$saksnummer_nav', saksnummer_ske='$saksnummer_ske', fildata_nav='$fildata_nav', jsondata_ske='$jsondata_ske', status='$status', dato_sendt=$dato_sendt, dato_siste_status=$dato_siste_status, kravtype='$kravtype')"
    }
}

