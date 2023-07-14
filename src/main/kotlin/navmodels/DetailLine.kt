package navmodels

import java.time.LocalDate

class DetailLine(
    val lineNummer: Int,
    val saksNummer: String,
    val belop: Double,
    val vedtakDato: LocalDate,
    val gjelderID: String,
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
    val fremtidigYtelse: String,
)
{
    override fun toString(): String {
        return "DetailLine(lineNummer=$lineNummer, saksNummer='$saksNummer', belop='$belop', vedtakDato=$vedtakDato, gjelderID='$gjelderID', periodeFOM='$periodeFOM', periodeTOM='$periodeTOM', kravkode='$kravkode', referanseNummerGammelSak='$referanseNummerGammelSak', transaksjonDato='$transaksjonDato', enhetBosted='$enhetBosted', enhetBehandlende='$enhetBehandlende', kodeHjemmel='$kodeHjemmel', kodeArsak='$kodeArsak', belopRente='$belopRente', fremtidigYtelse='$fremtidigYtelse')"
    }
}
