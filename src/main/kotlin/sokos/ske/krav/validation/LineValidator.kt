package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.isOpprettKrav
import java.time.LocalDate
import java.time.format.DateTimeFormatter


object LineValidator {
    private val logger = KotlinLogging.logger(this.javaClass.name)
    fun validateNewLines(file: FtpFil): List<KravLinje> {
        val allErrorMessages = mutableListOf<String>()
        val returnLines = file.kravLinjer.map {
            when (val result: ValidationResult = validateLine(it)) {
                is ValidationResult.Success -> {
                    it.copy(status = Status.KRAV_IKKE_SENDT.value)
                }
                is ValidationResult.Error -> {
                    allErrorMessages.addAll(result.messages)
                    it.copy(status = Status.VALIDERINGSFEIL_I_FIL.value)
                }
            }
        }
        if (allErrorMessages.isNotEmpty()) {
            Metrics.lineValidationError.labels(file.name, allErrorMessages.toString()).inc()
            logger.info ("Feil i validering av linjer i fil ${file.name}: $allErrorMessages" )
        }
        return returnLines
    }

    private fun validateLine(krav: KravLinje): ValidationResult {
        val errorMessages = mutableListOf<String>()

        val saksnrValid = validateSaksnr(krav.saksNummer)
        val vedtakDatoValid = validateVedtaksdato(krav.vedtakDato)
        val kravtypeValid = validateKravtype(krav)
        val refnrGammelSakValid = if (!krav.isOpprettKrav()) validateSaksnr(krav.referanseNummerGammelSak) else true
        val fomTomValid =
            validatePeriode(krav.periodeFOM, krav.periodeTOM)
        val utbetalingsDatoValid = validateUtbetalingsDato(krav.utbetalDato, krav.vedtakDato)


        if (!saksnrValid) {
            errorMessages.add("Saksnummer er ikke riktiog formatert og/eller inneholder ugyldige tegn (${krav.saksNummer}) på linje ${krav.linjeNummer}")
        }
        if (!vedtakDatoValid) {
            errorMessages.add("Vedtaksdato er kan ikke være i fremtiden. Dersom feltet i denne linjen viser +9999... er  datoen feil formatert : ${krav.vedtakDato} på linje ${krav.linjeNummer}")
        }
        if (!kravtypeValid) {
            errorMessages.add("Kravtype finnes ikke definert for oversendig til skatt : (${krav.kravKode} sammen med (${krav.hjemmelKode}) på linje ${krav.linjeNummer} ")
        }
        if (!refnrGammelSakValid) {
            errorMessages.add("Refnummer gammel sak er ikke riktiog formatert og/eller inneholder ugyldige tegn (${krav.referanseNummerGammelSak}) på linje ${krav.linjeNummer}\")")
        }
        if (!fomTomValid) {
            errorMessages.add("Periode(fom->tom) må være i fortid og FOM må være før TOM: (Fom: ${krav.periodeFOM} Tom: ${krav.periodeTOM} på linje ${krav.linjeNummer} ")
        }
        if (!utbetalingsDatoValid) {
            errorMessages.add("Utbetalingsdato må være i fortid og må være før vedtaksdato: (Utbetalinngsdato: ${krav.utbetalDato} Vedtaksdato: ${krav.vedtakDato} på linje ${krav.linjeNummer} ")
        }

        if (errorMessages.isNotEmpty()){
            return ValidationResult.Error(errorMessages)
        }
        return ValidationResult.Success(listOf(krav))
    }

    private fun validateKravtype(krav: KravLinje): Boolean = try {
        StonadsType.getStonadstype(krav)
        true
    }catch (e: NotImplementedError){
        false
    }

    private fun validateSaksnr(navSaksnr: String) = navSaksnr.matches("^[a-zA-Z0-9-/]+$".toRegex())

    private fun validateVedtaksdato(dato: LocalDate) = validateDateInPast(dato)

    private fun validateDateInPast(date: LocalDate) = !validateDateInFuture(date)

    private fun validateDateInFuture(date: LocalDate) = date.isAfter(LocalDate.now())

    private fun validatePeriode(fom: String, tom: String) = try {
        val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
        val dateFrom = LocalDate.parse(fom, dtf)
        val dateTo = LocalDate.parse(tom, dtf)
        (dateFrom == dateTo) || (dateFrom.isBefore(dateTo) && dateTo.isBefore(LocalDate.now()))
    } catch (e: Exception) {
        false
    }

    private fun validateUtbetalingsDato(utbetalingsDato: LocalDate, vedtaksDato: LocalDate) = validateDateInPast(utbetalingsDato) && utbetalingsDato.isBefore(vedtaksDato)

}
