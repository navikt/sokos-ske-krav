package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class KravTable(
    val kravId: Long,
    val saksnummerSKE: String,
    val saksnummerNAV: String,
    val belop: Double,
    val vedtakDato: LocalDate,
    val gjelderId: String,
    val periodeFOM: String,
    val periodeTOM: String,
    val kravkode: String,
    val referanseNummerGammelSak: String,
    val transaksjonDato: String,
    val enhetBosted: String,
    val enhetBehandlende: String,
    val kodeHjemmel: String,
    val kodeArsak: String,
    val belopRente: Double,
    val fremtidigYtelse: Double,
    val utbetalDato: String? = null,
    val fagsystemId: String? = null,
    val status: String,
    val datoSendt: LocalDateTime,
    val datoSisteStatus: LocalDateTime,
    val kravtype: String,
){
}

