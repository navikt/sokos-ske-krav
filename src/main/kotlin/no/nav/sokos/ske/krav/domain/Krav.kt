package no.nav.sokos.ske.krav.domain

import java.time.LocalDate
import java.time.LocalDateTime

const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"

data class Krav(
    val kravId: Long,
    val filnavn: String,
    val linjenummer: Int,
    val kravidentifikatorSKE: String,
    val saksnummerNAV: String,
    val belop: Double,
    val vedtaksDato: LocalDate,
    val gjelderId: String,
    val periodeFOM: String,
    val periodeTOM: String,
    val kravkode: String,
    val referansenummerGammelSak: String,
    val transaksjonsDato: String,
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
    val corrId: String,
    val tidspunktSendt: LocalDateTime?,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)
