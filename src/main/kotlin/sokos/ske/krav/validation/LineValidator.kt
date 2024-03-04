package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.KravtypeMappingFromNAVToSKE
import java.time.LocalDate


private val logger = KotlinLogging.logger {}

object LineValidator {

    fun validateLine(krav: KravLinje, filNavn: String): Boolean {
        val saksnrValid = validateSaksnr(krav.saksNummer)
        val belopValid = validateBelop(krav.belop.toLong())
        val vedtakDatoValid = validateVedtaksdato(krav.vedtakDato)
        val kravtypeValid = validateKravtype(KravtypeMappingFromNAVToSKE.getKravtype(krav))
        val refnrGammelValid = validateSaksnr(krav.referanseNummerGammelSak)
        val fomTomValid =
            validateFomBeforetom(
                LocalDate.of(
                    krav.periodeFOM.substring(0, 3).toInt(),
                    krav.periodeFOM.substring(4, 5).toInt(),
                    krav.periodeFOM.substring(6, 7).toInt()
                ),
                LocalDate.of(
                    krav.periodeTOM.substring(0, 3).toInt(),
                    krav.periodeTOM.substring(4, 5).toInt(),
                    krav.periodeTOM.substring(6, 7).toInt()
                )
            )

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

    private fun validateVedtaksdato(dato: LocalDate) = validateDatoIFortid(dato)

    private fun validateDatoIFortid(date: LocalDate) = !validateDatoIFremtid(date)

    private fun validateDatoIFremtid(date: LocalDate) = date.isAfter(LocalDate.now())

    private fun validateFomBeforetom(fom: LocalDate, tom: LocalDate) = fom.isBefore(tom)

}
