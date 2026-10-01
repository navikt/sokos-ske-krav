package no.nav.sokos.ske.krav.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Krav(
    val avsender: String,
    val belop: Double,
    val belopRente: Double,
    val corrId: String,
    val enhetBehandlende: String,
    val enhetBosted: String,
    val fagsystemId: String,
    val filnavn: String,
    val fremtidigYtelse: Double,
    val gjelderId: String,
    val kodeArsak: String,
    val kodeHjemmel: String,
    val kravId: Long,
    val kravidentifikatorSKE: String,
    val kravkode: String,
    val kravtype: String,
    val linjenummer: Int,
    val periodeFOM: String,
    val periodeTOM: String,
    val referansenummerGammelSak: String,
    val saksnummerNAV: String,
    val status: Status,
    val tidspunktOpprettet: LocalDateTime,
    val tidspunktSendt: LocalDateTime?,
    val tidspunktSisteStatus: LocalDateTime,
    val tilleggsfrist: LocalDate?,
    val transaksjonsDato: String,
    val utbetalDato: LocalDate,
    val vedtaksDato: LocalDate,
)
