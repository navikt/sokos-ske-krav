package sokos.ske.krav.domain.nav

import java.time.LocalDate

data class KravLinje(
    val linjeNummer: Int,
    val saksNummer: String,
    val belop: Double,
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
    val belopRente: Double,
    val fremtidigYtelse: Double,
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
    val sumAllTransactionLines: Double,
)