package sokos.ske.krav.database.models

import java.time.LocalDate
import java.time.LocalDateTime


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
    val utbetalDato: LocalDate,
    val fagsystemId: String,
    val status: String,
    val kravtype: String,
    val corr_id: String,
    val tidspunktSendt: LocalDateTime?,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KravTable

        if (saksnummerSKE != other.saksnummerSKE) return false
        if (saksnummerNAV != other.saksnummerNAV) return false
        if (belop != other.belop) return false
        if (vedtakDato != other.vedtakDato) return false
        if (gjelderId != other.gjelderId) return false
        if (periodeFOM != other.periodeFOM) return false
        if (periodeTOM != other.periodeTOM) return false
        if (kravkode != other.kravkode) return false
        if (referanseNummerGammelSak != other.referanseNummerGammelSak) return false
        if (transaksjonDato != other.transaksjonDato) return false
        if (enhetBosted != other.enhetBosted) return false
        if (enhetBehandlende != other.enhetBehandlende) return false
        if (kodeHjemmel != other.kodeHjemmel) return false
        if (kodeArsak != other.kodeArsak) return false
        if (belopRente != other.belopRente) return false
        if (fremtidigYtelse != other.fremtidigYtelse) return false
        if (utbetalDato != other.utbetalDato) return false
        if (fagsystemId != other.fagsystemId) return false

        return true
    }

}

