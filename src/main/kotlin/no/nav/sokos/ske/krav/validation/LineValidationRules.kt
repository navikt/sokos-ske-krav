package no.nav.sokos.ske.krav.validation

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.util.isOpprettKrav
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.KRAVTYPE_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.PERIODE_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.SAKSNUMMER_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.UTBETALINGSDATO_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.VEDTAKSDATO_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_FOM_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_TOM_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.SAKSNUMMER_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.UNKNOWN_DATE_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.VEDTAKSDATO_WRONG_FORMAT

/*
* Validerer med Skatteetatens synkrone regler:
* https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler
* utbetalingsDato = foreldelsesfristensUtgangspunkt
* vedtaksdato = fastsettelsesdato

*/
object LineValidationRules {
    fun runValidation(krav: KravLinje): ValidationResult {
        val errorMessages =
            buildList {
                with(krav) {
                    checkVedtaksDato(vedtaksDato)?.let { message ->
                        add(Pair(VEDTAKSDATO_ERROR, "$message: (Vedtaksdato: $vedtaksDato). Linje: $linjenummer"))
                    }

                    checkUtbetalingsDato(utbetalDato, vedtaksDato)?.let { message ->
                        add(Pair(UTBETALINGSDATO_ERROR, "$message: (Utbetalingsdato: $utbetalDato). Linje: $linjenummer"))
                    }

                    checkPeriode(periodeFOM.toDate(), periodeTOM.toDate())?.let { message ->
                        add(Pair(PERIODE_ERROR, "$message: (FOM:$periodeFOM, TOM: $periodeTOM). Linje: $linjenummer"))
                    }

                    if (!saksNummerIsValid(saksnummerNav)) {
                        add(Pair(SAKSNUMMER_ERROR, "$SAKSNUMMER_WRONG_FORMAT: ($saksnummerNav). Linje: $linjenummer"))
                    }

                    if (!kravTypeIsValid(krav)) {
                        add(Pair(KRAVTYPE_ERROR, "$KRAVTYPE_DOES_NOT_EXIST: ($kravKode) sammen med ($kodeHjemmel). Linje: $linjenummer"))
                    }

                    if (!referanseNummerGammelSakIsValid(referansenummerGammelSak, isOpprettKrav())) {
                        add(Pair(REFERANSENUMMERGAMMELSAK_ERROR, "$REFERANSENUMMERGAMMELSAK_WRONG_FORMAT: ($referansenummerGammelSak). Linje: $linjenummer"))
                    }
                }
            }

        return if (errorMessages.isNotEmpty()) {
            println("FEIL I KRAVLINJE $krav")
            println(errorMessages)
            ValidationResult.Error(errorMessages)
        } else {
            ValidationResult.Success(listOf(krav))
        }
    }

    // Fom-dato kan ikke være etter tom (kan være lik tom)
    // Tom-dato kan være frem i tid, men ikke lenger frem enn inneværende måned
    // Dvs, Tom-dato må være før neste måned
    private fun checkPeriode(
        periodeFOM: LocalDate,
        periodeTOM: LocalDate,
    ): String? =
        when {
            !periodeFOM.isAfter(periodeTOM) && periodeTOM.isBeforeNextMonth() -> null
            periodeFOM.isEqual(errorDate) -> PERIODE_FOM_WRONG_FORMAT
            periodeTOM.isEqual(errorDate) -> PERIODE_TOM_WRONG_FORMAT
            periodeFOM.isAfter(periodeTOM) -> PERIODE_FOM_IS_AFTER_PERIODE_TOM
            !periodeTOM.isBeforeNextMonth() -> PERIODE_TOM_IS_IN_INVALID_FUTURE
            else -> UNKNOWN_DATE_ERROR
        }

    // Vedtaksdato kan ikke være i fremtiden
    private fun checkVedtaksDato(vedtaksDato: LocalDate): String? =
        when {
            !vedtaksDato.isInFuture() -> null
            vedtaksDato.isEqual(errorDate) -> VEDTAKSDATO_WRONG_FORMAT
            vedtaksDato.isInFuture() -> VEDTAKSDATO_IS_IN_FUTURE
            else -> UNKNOWN_DATE_ERROR
        }

    // Utbetalingsdato må være før vedtaksdato
    private fun checkUtbetalingsDato(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
    ): String? =
        when {
            utbetalingsDato.isBefore(vedtaksDato) -> null
            utbetalingsDato.isEqual(errorDate) -> UTBETALINGSDATO_WRONG_FORMAT
            utbetalingsDato.isAfter(vedtaksDato) || utbetalingsDato.isEqual(vedtaksDato) -> UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
            else -> UNKNOWN_DATE_ERROR
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
            StonadsType.getStonadstype(krav.kravKode, krav.kodeHjemmel)
            true
        } catch (_: NotImplementedError) {
            false
        }

    private fun LocalDate.isInFuture() = this.isAfter(LocalDate.now())

    private fun LocalDate.isBeforeNextMonth(): Boolean {
        val next = LocalDate.now().plusMonths(1)
        val nextMonthStart = LocalDate.of(next.year, next.month, 1)

        return this.isBefore(nextMonthStart)
    }

    val errorDate: LocalDate = LocalDate.parse("21240101", DateTimeFormatter.ofPattern("yyyyMMdd"))

    object ErrorMessages {
        const val VEDTAKSDATO_WRONG_FORMAT = "Vedtaksdato er feil formattert i fil"
        const val VEDTAKSDATO_IS_IN_FUTURE = "Vedtaksdato kan ikke være i fremtiden"
        const val UTBETALINGSDATO_WRONG_FORMAT = "Utbetalingsdato er feil formattert i fil"
        const val UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO = "Utbetalingsdato må være tidligere enn vedtaksdato"
        const val PERIODE_FOM_WRONG_FORMAT = "FOM er feil formattert i fil"
        const val PERIODE_TOM_WRONG_FORMAT = "FOM er feil formattert i fil"
        const val PERIODE_FOM_IS_AFTER_PERIODE_TOM = "Periode FOM kan ikke være etter TOM"
        const val PERIODE_TOM_IS_IN_INVALID_FUTURE = "Periode TOM kan ikke være etter inneværende måned"
        const val UNKNOWN_DATE_ERROR = "Ukjent datofeil"
        const val SAKSNUMMER_WRONG_FORMAT = "Saksnummer er feil formattert i fil"
        const val REFERANSENUMMERGAMMELSAK_WRONG_FORMAT = "ReferanseNummerGammelSak er feil formattert i fil"
        const val KRAVTYPE_DOES_NOT_EXIST = "Kravtype finnes ikke definert for oversending til skatt"
    }

    object ErrorKeys {
        const val VEDTAKSDATO_ERROR = "Feil med vedtaksdato"
        const val UTBETALINGSDATO_ERROR = "Feil med utbetalingsdato"
        const val PERIODE_ERROR = "Feil med periode"
        const val SAKSNUMMER_ERROR = "Feil med saksnummer"
        const val REFERANSENUMMERGAMMELSAK_ERROR = "Feil med ReferanseNummerGammelSak"
        const val KRAVTYPE_ERROR = "Kravtype finnes ikke definert for oversending til skatt"
    }

    private fun String.toDate() =
        runCatching {
            LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyyMMdd"))
        }.getOrElse {
            errorDate
        }
}
