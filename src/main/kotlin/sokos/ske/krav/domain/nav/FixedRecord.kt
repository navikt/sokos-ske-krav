package sokos.ske.krav.domain.nav

import java.math.BigDecimal
import java.time.LocalDate

data class KravLinje(
    val linjeNummer: Int,
    val saksNummer: String,
    val belop: BigDecimal,
    val vedtakDato: LocalDate,
    val gjelderID: String,
    val periodeFOM: String,
    val periodeTOM: String,
    val kravKode: String,
    val referanseNummerGammelSak: String,
    val transaksjonDato: String,
    val enhetBosted: String,
    val enhetBehandlende: String,
    val hjemmelKode: String,
    val arsakKode: String,
    val belopRente: BigDecimal,
    val fremtidigYtelse: BigDecimal,
    val utbetalDato: LocalDate,
    val fagsystemId: String,
    val status: String? = null
)

data class KontrollLinjeHeader(
    val transaksjonDato: String,
    val avsender: String
)

data class KontrollLinjeFooter(
    val transaksjonDato: String,
    val avsender: String,
    val antallTransaksjoner: Int,
    val sumAlleTransaksjoner: BigDecimal,
)
