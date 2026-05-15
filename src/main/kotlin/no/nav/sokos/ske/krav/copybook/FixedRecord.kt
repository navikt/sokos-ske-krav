package no.nav.sokos.ske.krav.copybook

import java.math.BigDecimal
import java.time.LocalDate

import kotlin.math.roundToLong

import no.nav.sokos.ske.krav.domain.Status

sealed interface FtpLinje

data class ErrorLinje(
    val message: String,
) : FtpLinje

data class KravLinje(
    val linjenummer: Int,
    val saksnummerNav: String,
    val belop: BigDecimal,
    val vedtaksDato: LocalDate,
    val gjelderId: String,
    val periodeFOM: String,
    val periodeTOM: String,
    val kravKode: String,
    val referansenummerGammelSak: String,
    val transaksjonsDato: String,
    val enhetBosted: String,
    val enhetBehandlende: String,
    val kodeHjemmel: String,
    val kodeArsak: String,
    val belopRente: BigDecimal,
    val fremtidigYtelse: BigDecimal,
    val utbetalDato: LocalDate,
    val fagsystemId: String,
    val status: String? = null,
    val tilleggsfrist: LocalDate? = null,
    val avsender: String,
) : FtpLinje {
    fun isStopp() = belop.toDouble().roundToLong() == 0L

    fun isEndring() = referansenummerGammelSak.isNotEmpty() && !isStopp()

    fun isOpprettKrav() = !isEndring() && !isStopp()

    fun markAsValid() = copy(status = Status.KRAV_IKKE_SENDT.value)

    fun markAsValidationError() = copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
}

data class KontrollLinjeHeader(
    val transaksjonsDato: String,
    val avsender: String,
)

data class KontrollLinjeFooter(
    val transaksjonTimestamp: String,
    val avsender: String,
    val antallTransaksjoner: Int,
    val sumAlleTransaksjoner: BigDecimal,
)
