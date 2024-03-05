package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.KravtypeMappingFromNAVToSKE
import java.time.LocalDate
import java.time.format.DateTimeFormatter


private val logger = KotlinLogging.logger {}

object LineValidator {

    fun validateLine(krav: KravLinje, filNavn: String): Boolean {
        val saksnrValid = validateSaksnr(krav.saksNummer)
        val belopValid = validateBelop(krav.belop.toLong())
        val vedtakDatoValid = validateVedtaksdato(krav.vedtakDato)
        val kravtypeValid = validateKravtype(KravtypeMappingFromNAVToSKE.getKravtype(krav))
        val refnrGammelValid = validateSaksnr(krav.referanseNummerGammelSak)
        val fomTomValid =
            validateFomBeforetom(krav.periodeFOM, krav.periodeTOM)

        if (!saksnrValid) {
            //TODO lagre feilinformasjon
        }
        if (!belopValid) {
            //TODO lagre feilinformasjon
        }
        if (!vedtakDatoValid) {
            //TODO lagre feilinformasjon
        }
        if (!kravtypeValid) {
            //TODO lagre feilinformasjon
        }
        if (!refnrGammelValid) {
            //TODO lagre feilinformasjon
        }
        if (!fomTomValid) {
            //TODO lagre feilinformasjon
        }
        return saksnrValid && belopValid && vedtakDatoValid && kravtypeValid && refnrGammelValid && fomTomValid
    }

    private fun validateBelop(belop: Long) = belop > 0

    private fun validateKravtype(kravtypeSke: KravtypeMappingFromNAVToSKE?) = (kravtypeSke != null)

    //Må være mellom 1 og 40 tegn, og kun inneholde bokstaver (a-å, A-Å), tall og spesialtegnene - og /
    private fun validateSaksnr(navSaksnr: String) = navSaksnr.matches("^[a-zA-Z0-9-/]+$".toRegex())

    private fun validateVedtaksdato(dato: LocalDate) = validateDateInPast(dato)

    private fun validateDateInPast(date: LocalDate) = !validateDateInFuture(date)

    private fun validateDateInFuture(date: LocalDate) = date.isAfter(LocalDate.now())

    private fun validateFomBeforetom(fom: String, tom: String) = try {
        val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
        val dateFrom = LocalDate.parse(fom, dtf)
        val dateTo = LocalDate.parse(tom, dtf)
        dateFrom.isBefore(dateTo)
    } catch (e: Exception) {
        false
    }
}
