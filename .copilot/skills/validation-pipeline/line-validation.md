# Line Validation Patterns

## LineValidationRules — per-line business rules

`LineValidationRules` is a Kotlin `object`. Call `runValidation(krav: KravLinje)` to get a `ValidationResult`. Individual check functions return `null` on success or a human-readable `String` on failure — they **never throw**:

```kotlin
object LineValidationRules {
    fun runValidation(krav: KravLinje): ValidationResult {
        val errorMessages = buildList {
            with(krav) {
                checkVedtaksDato(vedtaksDato)?.let { msg ->
                    add(VEDTAKSDATO_ERROR to "$msg: (Vedtaksdato: $vedtaksDato). Linje: $linjenummer")
                }
                checkUtbetalingsDato(utbetalDato, vedtaksDato, avsender)?.let { msg ->
                    add(UTBETALINGSDATO_ERROR to "$msg: (Utbetalingsdato: $utbetalDato). Linje: $linjenummer")
                }
                checkPeriode(periodeFOM.toDate(), periodeTOM.toDate())?.let { msg ->
                    add(PERIODE_ERROR to "$msg: (FOM:$periodeFOM, TOM: $periodeTOM). Linje: $linjenummer")
                }
                checkTilleggsfristDato(tilleggsfrist)?.let { msg ->
                    add(TILLEGGSFRISTDATO_ERROR to msg)
                }
                if (avsender.trim() == Avsender.OB04.name) {
                    checkFagsystemId(fagsystemId)?.let { msg ->
                        add(FAGSYSTEMID_ERROR to "$msg. Linje: $linjenummer")
                    }
                }
                checkGjelderId(gjelderId)?.let { msg ->
                    add(GJELDERID_ERROR to "$msg. Linje: $linjenummer")
                }
                checkBelop(belop)?.let { msg ->
                    add(HOVEDSTOL_ERROR to "$msg: (Beløp: $belop). Linje: $linjenummer")
                }
                if (!saksNummerIsValid(saksnummerNav)) {
                    add(SAKSNUMMER_ERROR to "$SAKSNUMMER_WRONG_FORMAT: ($saksnummerNav). Linje: $linjenummer")
                }
            }
        }
        return if (errorMessages.isEmpty()) {
            ValidationResult.Success(emptyList())
        } else {
            ValidationResult.Error(errorMessages)
        }
    }

    // Check functions: return null = OK, return String = error message
    private fun checkVedtaksDato(date: LocalDate): String? { ... }
    private fun checkBelop(belop: BigDecimal): String? { ... }
    // etc.
}
```

Error key constants live on `LineValidationRules.ErrorKeys`; error message string constants live on `LineValidationRules.ErrorMessages`.

## LineValidator — orchestrates line-level validation

`LineValidator.validateNewLines()` maps every `KravLinje` through `LineValidationRules`, sets `status` on each line, persists line errors to DB, and flushes Slack once at the end:

```kotlin
class LineValidator(
    private val slackService: SlackService = SlackService(),
) {
    suspend fun validateNewLines(
        file: FtpFil,
        dbService: DatabaseService,
    ): List<KravLinje> {
        val slackMessages = mutableListOf<Pair<String, String>>()

        val returnLines = file.kravLinjer.map { linje ->
            Metrics.numberOfKravRead.increment()
            when (val result = LineValidationRules.runValidation(linje)) {
                is ValidationResult.Success ->
                    linje.copy(status = Status.KRAV_IKKE_SENDT.value)
                is ValidationResult.Error -> {
                    slackMessages.addAll(result.messages)
                    dbService.saveLineValidationError(
                        file.name,
                        linje,
                        result.messages.joinToString { it.second },
                    )
                    linje.copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                }
            }
        }

        if (slackMessages.isNotEmpty()) {
            logger.warn("Feil i validering av linjer i fil ${file.name}")
            slackService.addError(file.name, "Feil i linjevalidering", slackMessages)
        }
        slackService.sendErrors()  // safe to call even when there are no errors
        return returnLines
    }
}
```

The complete list of lines (both valid and invalid) is always returned — invalid lines are not dropped, just marked.
