package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.isOpprettKrav
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class LineValidator {
    private val logger = KotlinLogging.logger("secureLogger")

    fun validateNewLines(
        file: FtpFil,
        ds: DatabaseService,
        slackClient: SlackClient = SlackClient()
    ): List<KravLinje> {
        val allErrorMessages = mutableListOf<List<String>>()
        val returnLines =
            file.kravLinjer.map {
                when (val result: ValidationResult = validateLine(it)) {
                    is ValidationResult.Success -> {
                        it.copy(status = Status.KRAV_IKKE_SENDT.value)
                    }

                    is ValidationResult.Error -> {
                        allErrorMessages.addAll(result.messages)
                        ds.saveValidationError(file.name, it, result.messages.joinToString())
                        slackClient.sendLinjevalideringsMelding(file.name, result.messages)
                        it.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
            }
        if (allErrorMessages.isNotEmpty()) {
            Metrics.registerLineValidationError(file.name, allErrorMessages.toString()).increment()
            logger.warn("Feil i validering av linjer i fil ${file.name}: $allErrorMessages")
        }
        return returnLines
    }

    private fun validateLine(krav: KravLinje): ValidationResult {
        val errorMessages = mutableListOf<List<String>>()

        val saksnrValid = validateSaksnr(krav.saksnummerNav)
        val vedtakDatoValid = validateVedtaksdato(krav.vedtaksDato)
        val kravtypeValid = validateKravtype(krav)
        val refnrGammelSakValid = if (!krav.isOpprettKrav()) validateSaksnr(krav.referansenummerGammelSak) else true
        val fomTomValid = validatePeriode(krav.periodeFOM, krav.periodeTOM)
        val utbetalingsDatoValid = validateUtbetalingsDato(krav.utbetalDato, krav.vedtaksDato)

        if (!saksnrValid) {
            errorMessages.add(listOf("Feil i Saksnr.", "Saksnummer er ikke riktig formatert og/eller inneholder ugyldige tegn (${krav.saksnummerNav}) på linje ${krav.linjenummer}\n"))
        }
        if (!vedtakDatoValid) {
            errorMessages.add(listOf("Feil i vedtaksdato","Vedtaksdato er kan ikke være i fremtiden. Dersom feltet i denne linjen viser +9999... er  datoen feil formatert : ${krav.vedtaksDato} på linje ${krav.linjenummer}\n"))
        }
        if (!kravtypeValid) {
            errorMessages.add(listOf("Feil med kravtype","Kravtype finnes ikke definert for oversending til skatt : (${krav.kravKode} sammen med (${krav.kodeHjemmel}) på linje ${krav.linjenummer} \n"))
        }
        if (!refnrGammelSakValid) {
            errorMessages.add(listOf("Feili refnr gammel sak", "Refnummer gammel sak er ikke riktig formatert og/eller inneholder ugyldige tegn (${krav.referansenummerGammelSak}) på linje ${krav.linjenummer}\")\n"))
        }
        if (!fomTomValid) {
            errorMessages.add(listOf("Feil med FOM og/eller TOM", "Periode(fom->tom) må være i fortid og FOM må være før TOM: (Fom: ${krav.periodeFOM} Tom: ${krav.periodeTOM} på linje ${krav.linjenummer} \n"))
        }
        if (!utbetalingsDatoValid) {
            errorMessages.add(listOf("Feil i utbetalingsdato", "Utbetalingsdato må være i fortid og må være før vedtaksdato: (Utbetalinngsdato: ${krav.utbetalDato} Vedtaksdato: ${krav.vedtaksDato} på linje ${krav.linjenummer} \n"))
        }

        return if (errorMessages.isNotEmpty()) {
            ValidationResult.Error(errorMessages)
        } else {
            ValidationResult.Success(listOf(krav))
        }
    }

    private fun validateSaksnr(navSaksnr: String) = navSaksnr.matches("^[a-zA-Z0-9-/]+$".toRegex())

    private fun validateVedtaksdato(date: LocalDate) = validateDateInPast(date)

    private fun validateUtbetalingsDato(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
    ) = validateDateInPast(utbetalingsDato) && utbetalingsDato.isBefore(vedtaksDato)

    private fun validateKravtype(krav: KravLinje): Boolean =
        try {
            StonadsType.getStonadstype(krav)
            true
        } catch (e: NotImplementedError) {
            false
        }

    private fun validatePeriode(
        fom: String,
        tom: String,
    ) = try {
        val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
        val dateFrom = LocalDate.parse(fom, dtf)
        val dateTo = LocalDate.parse(tom, dtf)
        (dateFrom == dateTo || dateFrom.isBefore(dateTo)) && dateTo.isBefore(LocalDate.now())
    } catch (e: DateTimeParseException) {
        false
    }

    private fun validateDateInPast(date: LocalDate) = !date.isAfter(LocalDate.now())
}
