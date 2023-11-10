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
) {
    override fun toString(): String {
        return "DetailLine(lineNummer=$linjeNummer, saksNummer='$saksNummer', belop=$belop, vedtakDato=$vedtakDato, gjelderID='$gjelderID', periodeFOM='$periodeFOM', periodeTOM='$periodeTOM', kravkode='$stonadsKode', referanseNummerGammelSak='$referanseNummerGammelSak', transaksjonDato='$transaksjonDato', enhetBosted='$enhetBosted', enhetBehandlende='$enhetBehandlende', kodeHjemmel='$hjemmelKode', kodeArsak='$arsakKode', belopRente=$belopRente, fremtidigYtelse=$fremtidigYtelse, utbetalDato=$utbetalDato, fagsystemId=$fagsystemId)"
    }
}

data class FirstLine(
    val transferDate: String,
    val sender: String
) {
    override fun toString(): String {
        return "FirstLine(transferDate=$transferDate, sender='$sender')"
    }
}

data class LastLine(
    val transferDate: String,
    val sender: String,
    val numTransactionLines: Int,
    val sumAllTransactionLines: Double,
) {
    override fun toString(): String {
        return "LastLine(transferDate=$transferDate, sender='$sender', numTransactionLines=$numTransactionLines, sumAllTransactionLines=$sumAllTransactionLines)"
    }
}