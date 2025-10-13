package no.nav.sokos.ske.krav.domain.nav

import java.math.BigDecimal
import java.time.LocalDate

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
)

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
