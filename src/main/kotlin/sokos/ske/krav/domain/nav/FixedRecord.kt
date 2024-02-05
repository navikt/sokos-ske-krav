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
    val stonadsKode: String,
    val referanseNummerGammelSak: String,
    val transaksjonDato: String,
    val enhetBosted: String,
    val enhetBehandlende: String,
    val hjemmelKode: String,
    val arsakKode: String,
    val belopRente: BigDecimal,
    val fremtidigYtelse: BigDecimal,
    val utbetalDato: String?,
    val fagsystemId: String?,
)

data class FirstLine(
    val transferDate: String,
    val sender: String
)

data class LastLine(
    val transferDate: String,
    val sender: String,
    val numTransactionLines: Int,
    val sumAllTransactionLines: BigDecimal,
)