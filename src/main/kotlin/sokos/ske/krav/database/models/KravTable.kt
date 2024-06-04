package sokos.ske.krav.database.models

import java.time.LocalDate
import java.time.LocalDateTime


data class KravTable(
    val kravId: Long,
    val filnavn: String,
    val linjenummer: Int,
    val kravidentifikatorSKE: String,
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
    val utbetalDato: LocalDate,
    val fagsystemId: String,
    val status: String,
    val kravtype: String,
    val corr_id: String,
    val tidspunktSendt: LocalDateTime?,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)
