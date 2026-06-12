package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.domain.StonadsType

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
                        add(Pair(ErrorKeys.SAKSNUMMER_ERROR, "${ErrorMessages.SAKSNUMMER_WRONG_FORMAT}: ($saksnummerNav). Linje: $linjenummer"))
                    }

                    if (!kravTypeIsValid(krav)) {
                        add(Pair(ErrorKeys.KRAVTYPE_ERROR, "${ErrorMessages.KRAVTYPE_DOES_NOT_EXIST}: ($kravKode) sammen med ($kodeHjemmel). Linje: $linjenummer"))
                    }

                    if (!isOpprettKrav()) {
                        if (isStopp() && referansenummerGammelSak.isEmpty()) {
                            add(Pair(ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING, "${ErrorMessages.REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP}. Linje: $linjenummer"))
                        } else if (!saksNummerIsValid(referansenummerGammelSak)) {
                            add(Pair(ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR, "${ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT}: ($referansenummerGammelSak). Linje: $linjenummer"))
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
    ): ErrorMessages? =
        when {
            !periodeFOM.isAfter(periodeTOM) && periodeTOM.isBeforeNextMonth() -> null
            periodeFOM.isEqual(errorDate) -> ErrorMessages.PERIODE_FOM_WRONG_FORMAT
            periodeTOM.isEqual(errorDate) -> ErrorMessages.PERIODE_TOM_WRONG_FORMAT
            periodeFOM.isAfter(periodeTOM) -> ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
            !periodeTOM.isBeforeNextMonth() -> ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
            else -> ErrorMessages.UNKNOWN_DATE_ERROR
        }

    // Vedtaksdato kan ikke være i fremtiden
    private fun checkVedtaksDato(vedtaksDato: LocalDate): ErrorMessages? =
        when {
            !vedtaksDato.isInFuture() -> null
            vedtaksDato.isEqual(errorDate) -> ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
            vedtaksDato.isInFuture() -> ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
            else -> ErrorMessages.UNKNOWN_DATE_ERROR
        }

    // Tillater tom utbetalingsdato for Arena, Pesys, Infotryd. Ellers må den være før vedtaksdato
    private fun checkUtbetalingsDato(
        utbetalingsDato: LocalDate,
        vedtaksDato: LocalDate,
        avsender: String,
    ): ErrorMessages? =
        when {
            avsender.trim() == Avsender.OB04.name && utbetalingsDato.isEqual(errorDate) -> ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
            utbetalingsDato.isEqual(errorDate) -> null
            utbetalingsDato.isBefore(vedtaksDato) -> null
            utbetalingsDato.isEqual(vedtaksDato) || utbetalingsDato.isAfter(vedtaksDato) -> ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
            else -> ErrorMessages.UNKNOWN_DATE_ERROR
        }

    // Datoen kan ikke være lengre tilbake i tid enn 10 måneder fra dagens dato
    private fun checkTilleggsfristDato(tilleggsFristDato: LocalDate?): ErrorMessages? =
        when {
            tilleggsFristDato == null -> null
            tilleggsFristDato.isEqual(errorDate) -> ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT
            tilleggsFristDato.isBefore(LocalDate.now().minusMonths(10)) -> ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD
            else -> null
        }

    private fun checkGjelderId(gjelderId: String): ErrorMessages? = if (gjelderId.isBlank()) ErrorMessages.GJELDERID_MISSING else null

    private fun checkFagsystemId(fagsystemId: String): ErrorMessages? = if (fagsystemId.isBlank()) ErrorMessages.FAGSYSTEMID_MISSING else null

    private fun checkBelop(belop: BigDecimal): ErrorMessages? = if (belop < BigDecimal.ZERO) ErrorMessages.BELOP_NEGATIVE else null

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

    private fun String.toDate() =
        runCatching {
            LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyyMMdd"))
        }.getOrElse {
            errorDate
        }
}
