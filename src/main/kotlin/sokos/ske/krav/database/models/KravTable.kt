package sokos.ske.krav.database.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class KravTable(
    val kravId: Long,
    val saksnummer_ske: String,
    val saksnummer: String,
    val belop: Double,
    val vedtakDato: kotlinx.datetime.LocalDate,
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
    val utbetalDato: LocalDate? = null,
    val fagsystemId: String? = null,
    val status: String,
    val dato_sendt: LocalDateTime,
    val dato_siste_status: LocalDateTime,
    val kravtype: String,
    val filnavn: String
){
}

