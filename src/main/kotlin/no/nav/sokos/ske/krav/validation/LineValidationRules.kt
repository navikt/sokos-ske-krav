package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.BELOP_NEGATIVE
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.FAGSYSTEMID_MISSING
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.GJELDERID_MISSING
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_FOM_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.PERIODE_TOM_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.SAKSNUMMER_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.UNKNOWN_DATE_ERROR
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
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
                        add(Pair(ErrorKeys.VEDTAKSDATO_ERROR, "$message: (Vedtaksdato: $vedtaksDato). Linje: $linjenummer"))
                    }

                    checkUtbetalingsDato(utbetalDato, vedtaksDato, avsender)?.let { message ->
                        add(Pair(ErrorKeys.UTBETALINGSDATO_ERROR, "$message: (Utbetalingsdato: $utbetalDato). Linje: $linjenummer"))
                    }

                    checkPeriode(periodeFOM.toDate(), periodeTOM.toDate())?.let { message ->
                        add(Pair(ErrorKeys.PERIODE_ERROR, "$message: (FOM:$periodeFOM, TOM: $periodeTOM). Linje: $linjenummer"))
                    }

                    checkTilleggsfristDato(tilleggsfrist)?.let { message ->
                        add(Pair(ErrorKeys.TILLEGGSFRISTDATO_ERROR, "$message: (Tilleggsfristdato: $tilleggsfrist). Saksnummer: $saksnummerNav. Linje: $linjenummer"))
                    }

                    if (avsender.trim() == Avsender.OB04.name) {
                        checkFagsystemId(fagsystemId)?.let { message ->
                            add(Pair(ErrorKeys.FAGSYSTEMID_ERROR, "$message. Linje: $linjenummer"))
                        }
                    }

                    checkGjelderId(gjelderId)?.let { message ->
                        add(Pair(ErrorKeys.GJELDERID_ERROR, "$message. Linje: $linjenummer"))
                    }

                    checkBelop(belop)?.let { message ->
                        add(Pair(ErrorKeys.HOVEDSTOL_ERROR, "$message: (Beløp: $belop). Linje: $linjenummer"))
                    }

                    if (!saksNummerIsValid(saksnummerNav)) {
                        add(Pair(ErrorKeys.SAKSNUMMER_ERROR, "$SAKSNUMMER_WRONG_FORMAT: ($saksnummerNav). Linje: $linjenummer"))
                    }

                    if (!kravTypeIsValid(krav)) {
                        add(Pair(ErrorKeys.KRAVTYPE_ERROR, "$KRAVTYPE_DOES_NOT_EXIST: ($kravKode) sammen med ($kodeHjemmel). Linje: $linjenummer"))
                    }

                    if (!isOpprettKrav()) {
                        if (isStopp() && referansenummerGammelSak.isEmpty()) {
                            add(Pair(ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING, "$REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP. Linje: $linjenummer"))
                        } else if (!saksNummerIsValid(referansenummerGammelSak)) {
                            add(Pair(ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR, "$REFERANSENUMMERGAMMELSAK_WRONG_FORMAT: ($referansenummerGammelSak). Linje: $linjenummer"))
                        }
                    }
                }
            }

        return if (errorMessages.isNotEmpty()) {
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

    // Tillater tom utbetalingsdato for Arena, Pesys, Infotryd. Ellers må den være før vedtaksdato
    private fun checkUtbetalingsDato(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
        avsender: String,
    ): String? =
        when {
            avsender.trim() == Avsender.OB04.name && utbetalingsDato.isEqual(errorDate) -> ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
            utbetalingsDato.isEqual(errorDate) -> null
            utbetalingsDato.isBefore(vedtaksDato) -> null
            utbetalingsDato.isEqual(vedtaksDato) || utbetalingsDato.isAfter(vedtaksDato) -> UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
            else -> UNKNOWN_DATE_ERROR
        }

    // Datoen kan ikke være lengre tilbake i tid enn 10 måneder fra dagens dato
    private fun checkTilleggsfristDato(tilleggsFristDato: LocalDate?): String? =
        when {
            tilleggsFristDato == null -> null
            tilleggsFristDato.isEqual(errorDate) -> TILLEGGSFRISTDATO_WRONG_FORMAT
            tilleggsFristDato.isBefore(LocalDate.now().minusMonths(10)) -> TILLEGGSFRISTDATO_TOO_OLD
            else -> null
        }

    private fun checkGjelderId(gjelderId: String): String? = if (gjelderId.isBlank()) GJELDERID_MISSING else null

    private fun checkFagsystemId(fagsystemId: String): String? = if (fagsystemId.isBlank()) FAGSYSTEMID_MISSING else null

    private fun checkBelop(belop: BigDecimal): String? = if (belop < BigDecimal.ZERO) BELOP_NEGATIVE else null

    // Saksnummer
    private fun saksNummerIsValid(navSaksnr: String) = navSaksnr.matches("^[a-zA-Z0-9-/]+$".toRegex())

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
        const val PERIODE_TOM_WRONG_FORMAT = "TOM er feil formattert i fil"
        const val PERIODE_FOM_IS_AFTER_PERIODE_TOM = "Periode FOM kan ikke være etter TOM"
        const val PERIODE_TOM_IS_IN_INVALID_FUTURE = "Periode TOM kan ikke være etter inneværende måned"
        const val UNKNOWN_DATE_ERROR = "Ukjent datofeil"
        const val SAKSNUMMER_WRONG_FORMAT = "Saksnummer er feil formattert i fil"
        const val REFERANSENUMMERGAMMELSAK_WRONG_FORMAT = "ReferanseNummerGammelSak er feil formattert i fil"
        const val REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP = "ReferanseNummerGammelSak mangler for stopp i fil"
        const val KRAVTYPE_DOES_NOT_EXIST = "Kravtype finnes ikke definert for oversending til skatt"
        const val TILLEGGSFRISTDATO_TOO_OLD = "Tilleggsfristdato kan ikke være lengre tilbake i tid enn 10 måneder fra dagens dato"
        const val TILLEGGSFRISTDATO_WRONG_FORMAT = "Tilleggsfristdato er feil formattert i fil"
        const val GJELDERID_MISSING = "gjelderId mangler"
        const val FAGSYSTEMID_MISSING = "fagsystemId mangler"
        const val BELOP_NEGATIVE = "Beløp kan ikke være negativt"
    }

    private fun String.toDate() =
        runCatching {
            LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyyMMdd"))
        }.getOrElse {
            errorDate
        }
}
