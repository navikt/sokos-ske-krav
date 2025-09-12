package no.nav.sokos.ske.krav.dto.nav

import java.math.BigDecimal
import java.time.LocalDate

import kotlin.math.roundToLong

import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV

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
)

fun KravLinje.isOpprettKrav() = (!this.isEndring() && !this.isStopp())

fun KravLinje.isEndring() = (referansenummerGammelSak.isNotEmpty() && !isStopp())

fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)

fun KravLinje.type(): String =
    when {
        this.isStopp() -> STOPP_KRAV
        this.isEndring() -> ENDRING_HOVEDSTOL
        else -> NYTT_KRAV
    }
