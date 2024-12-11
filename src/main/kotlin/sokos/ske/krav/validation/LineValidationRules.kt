package sokos.ske.krav.validation

import sokos.ske.krav.domain.StonadsType
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.util.isOpprettKrav
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
* Validerer med Skatteetatens synkrone regler:
* https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler
* foreldelsesfristensUtgangspunkt = utbetalingsDato
* fastsettelsesdato = vedtaksdato
*/
object LineValidationRules {
    fun runValidation(krav: KravLinje): ValidationResult {
        val errorMessages =
            buildList {
                with(krav) {
                    if (!saksNummerIsValid(saksnummerNav)) {
                        add(Pair("Feil i Saksnr", "Saksnummer er ikke riktig formatert og/eller inneholder ugyldige tegn ($saksnummerNav). Linje: ${linjenummer}\n"))
                    }
                    if (!vedtaksDatoIsValid(vedtaksDato)) {
                        // TODO: Bekreft dette
                        val message = checkVedtaksDatoRules(vedtaksDato)
                        add(Pair("Feil med vedtaksdato", message + "\n Vedtaksdato: $vedtaksDato. Linje: ${linjenummer}\n"))
                    }
                    if (!kravTypeIsValid(krav)) {
                        add(Pair("Kravtype finnes ikke definert for oversending til skatt ", "($kravKode) sammen med ($kodeHjemmel). Linje: ${linjenummer}\n"))
                    }
                    if (!referanseNummerGammelSakIsValid(referansenummerGammelSak, isOpprettKrav())) {
                        add(Pair("Feil i refnr gammel sak", "Refnummer gammel sak er ikke riktig formatert og/eller inneholder ugyldige tegn ($referansenummerGammelSak). Linje: ${linjenummer}\n"))
                    }
                    if (!periodeIsValid(periodeFOM, periodeTOM, kravKode)) {
                        val message = checkPeriodeRules(periodeFOM.toDate(), periodeTOM.toDate(), kravKode)
                        add(Pair("Feil med periode", message + "\n FOM:$periodeFOM, TOM: $periodeTOM. Linje: ${linjenummer}\n"))
                    }
                    if (!utbetalingsDatoIsValid(utbetalDato, vedtaksDato)) {
                        val message = checkUtbetalingsDatoRules(utbetalDato, vedtaksDato)
                        add(Pair("Feil med utbetalingsdato/vedtaksdato", message + "\n Utbetalingsdato:$utbetalDato, Vedtaksdato: $vedtaksDato. Linje: ${linjenummer}\n"))
                    }
                }
            }

        return if (errorMessages.isNotEmpty()) {
            ValidationResult.Error(errorMessages)
        } else {
            ValidationResult.Success(listOf(krav))
        }
    }

    private fun LocalDate.isInFuture() = this.isAfter(LocalDate.now())

    // Vedtaksdato
    private fun vedtaksDatoIsValid(date: LocalDate) = !date.isInFuture()

    private fun checkVedtaksDatoRules(vedtaksDato: LocalDate) =
        when {
            vedtaksDato == errorDate -> "Vedtaksdato er feil formattert i fil"
            vedtaksDato.isInFuture() -> "Vedtaksdato kan ikke være i fremtiden"
            else -> "Ukjent datofeil"
        }

    // Utbetalingsdato

    private fun utbetalingsDatoIsValid(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
    ) = !utbetalingsDato.isInFuture() && utbetalingsDato.isBefore(vedtaksDato)

    private fun checkUtbetalingsDatoRules(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
    ) = when {
        utbetalingsDato == errorDate -> "Utbetalingsdato er feil formattert i fil"
        utbetalingsDato.isAfter(vedtaksDato) || utbetalingsDato == vedtaksDato -> "Utbetalingsdato må være tidligere enn vedtaksdato"
        else -> "Ukjent datofeil"
    }

    // Periode
    private fun periodeIsValid(
        fom: String,
        tom: String,
        kravkode: String,
    ): Boolean {
        val dateFrom = fom.toDate()
        val dateTo = tom.toDate()

        if (dateFrom == errorDate || dateTo == errorDate) {
            return false
        }

        // TODO: Hør med steinar om dette blir riktig
        if (kravkode == "FO FT") {
            return (dateFrom == dateTo || dateFrom.isBefore(dateTo))
        }
        return (dateFrom == dateTo || dateFrom.isBefore(dateTo)) && dateTo.isBefore(LocalDate.now())
    }

    private fun checkPeriodeRules(
        periodeFom: LocalDate,
        periodeTom: LocalDate,
        kravKode: String,
    ) = when {
        periodeFom == errorDate -> "FOM er feil formattert i fil"
        periodeTom == errorDate -> "TOM er feil formattert i fil"
        periodeFom.isBefore(periodeTom) -> "FOM må være før TOM. "
        periodeTom.isInFuture() && kravKode != "FO FT" -> "Periode(fom->tom) må være i fortid. "
        else -> "Ukjent datofeil"
    }

    // Saksnummer

    private fun saksNummerIsValid(navSaksnr: String) = navSaksnr.matches("^[a-zA-Z0-9-/]+$".toRegex())

    private fun referanseNummerGammelSakIsValid(
        referansenummerGammelSak: String,
        isOpprettKrav: Boolean,
    ) = if (!isOpprettKrav) saksNummerIsValid(referansenummerGammelSak) else true

    // Kravtype
    private fun kravTypeIsValid(krav: KravLinje): Boolean =
        try {
            StonadsType.getStonadstype(krav)
            true
        } catch (e: NotImplementedError) {
            false
        }

    val errorDate: LocalDate = LocalDate.parse("21240101", DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun String.toDate() =
        runCatching {
            LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyyMMdd"))
        }.getOrElse {
            errorDate
        }
}
