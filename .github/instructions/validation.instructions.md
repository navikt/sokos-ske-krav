---
applyTo: "**/validation/**/*.kt,**/copybook/**/*.kt"
---

Valideringsmønstre: ValidationResult sealed class, FileValidator, LineValidationRules, LineValidator og FileParser for fixed-width filer.

> Validation is a two-phase pipeline: **file-level** (`FileValidator`) then **line-level** (`LineValidator` + `LineValidationRules`). Both produce `ValidationResult`. File errors move the whole file to `/inbound/feilfiler`; line errors mark individual lines as `VALIDERINGSFEIL_AV_LINJE_I_FIL` and keep them in the DB.

# Validation & File Parsing Patterns

## ValidationResult sealed class

All validation functions return `ValidationResult`. Never throw exceptions for expected business-rule failures — return `ValidationResult.Error` instead:

```kotlin
sealed class ValidationResult {
    data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
    data class Error(val messages: List<Pair<String, String>>) : ValidationResult()
}
```

`messages` is a list of `(errorKey, humanReadableMessage)` pairs. The `errorKey` identifies the failure type (used as the Slack header/tag key); the human-readable message includes the field value and line number.

## FileValidator — file-level rules

`FileValidator.validateFile()` runs against the full file content before any lines are processed. Returns `ValidationResult.Success` (with parsed `kravLinjer`) or `ValidationResult.Error`.

### Error keys (constants on `FileValidator.ErrorKeys`)

| Key | Meaning |
|---|---|
| `PARSE_EXCEPTION` | Uncaught exception during parsing |
| `FEIL_I_ANTALL` | Line count doesn't match `KontrollLinjeFooter.antallTransaksjoner` |
| `FEIL_I_SUM` | Checksum doesn't match `KontrollLinjeFooter.sumAlleTransaksjoner` |
| `FEIL_I_DATO` | Transaction date mismatch between header and footer (OS files only) |
| `FAGSYSTEMID_MANGLER` | One or more kravlinjer missing `fagsystemId` |

### Pattern

```kotlin
class FileValidator(
    private val slackService: SlackService = SlackService(),
) {
    suspend fun validateFile(
        content: List<String>,
        fileName: String,
    ): ValidationResult {
        val parser = FileParser(content)
        val errorMessages = buildList {
            runCatching {
                val footer = parser.parseKontrollLinjeFooter()
                val header = parser.parseKontrollLinjeHeader()
                val kravLinjer = runCatching { parser.parseKravLinjer() }
                    .onFailure { add(ErrorKeys.PARSE_EXCEPTION to (it.message ?: "Ukjent feil")) }
                    .getOrNull() ?: return@buildList

                validateLines(footer, header, kravLinjer)
            }.onFailure {
                add(ErrorKeys.PARSE_EXCEPTION to (it.message ?: "Ukjent feil"))
            }
        }

        if (errorMessages.isEmpty()) return ValidationResult.Success(parser.parseKravLinjer())

        logger.warn("*** Feil i validering av fil $fileName ***")
        slackService.addError(fileName, "Feil i validering av fil", errorMessages)
        slackService.sendErrors()
        return ValidationResult.Error(messages = errorMessages)
    }
}
```

Key rules:
- Use `buildList {}` to accumulate **all** errors before returning — do not short-circuit on first failure
- Always call `slackService.sendErrors()` before returning `ValidationResult.Error`
- `runCatching` around the outer parse call catches `ParseException` from `FileParser`

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

## FileParser — fixed-width copybook parser

`FileParser` uses positional `substring()` extraction (no delimiter). All positions are **0-based character offsets**.

### File structure

```
Line 1        KontrollLinjeHeader   sender (avsender) + transaction date
Lines 2..N-1  KravLinje             one claim per line, 200+ chars wide
Line N         KontrollLinjeFooter  record count + checksum sum
```

### Data classes

```kotlin
data class KravLinje(
    val linjenummer: Int,
    val saksnummerNav: String,
    val belop: BigDecimal,
    val vedtaksDato: LocalDate,
    val gjelderId: String,
    val periodeFOM: String,              // stored as "yyyyMMdd" String
    val periodeTOM: String,              // stored as "yyyyMMdd" String
    val kravKode: String,
    val referansenummerGammelSak: String,
    val transaksjonsDato: String,
    val enhetBosted: String,
    val enhetBehandlende: String,
    val kodeHjemmel: String,
    val kodeArsak: String,
    val belopRente: BigDecimal,
    val fremtidigYtelse: BigDecimal,
    val utbetalDato: LocalDate,
    val fagsystemId: String,
    val status: String? = null,
    val tilleggsfrist: LocalDate? = null,
    val avsender: String,
)

data class KontrollLinjeHeader(val transaksjonsDato: String, val avsender: String)
data class KontrollLinjeFooter(
    val transaksjonTimestamp: String,
    val avsender: String,
    val antallTransaksjoner: Int,
    val sumAlleTransaksjoner: BigDecimal,
)
```

### Byte positions for KravLinje

| Field | start | end |
|---|---|---|
| linjenummer | 4 | 11 |
| saksnummerNav | 11 | 29 |
| belop | 29 | 40 |
| vedtaksDato | 40 | 48 |
| gjelderId | 48 | 59 |
| periodeFOM | 59 | 67 |
| periodeTOM | 67 | 75 |
| kravKode | 75 | 83 |
| referansenummerGammelSak | 83 | 101 |
| transaksjonsDato | 101 | 109 |
| enhetBosted | 109 | 113 |
| enhetBehandlende | 113 | 117 |
| kodeHjemmel | 117 | 119 |
| kodeArsak | 119 | 131 |
| belopRente | 131 | 151 |
| fremtidigYtelse | 151 | 162 |
| utbetalDato | 162 | 170 |
| fagsystemId | 170 | 200 |
| tilleggsfrist | 200 | 208 |

### Private extraction helpers (extension functions on `String` inside `FileParser`)

```kotlin
private fun String.getString(start: Int, end: Int): String =
    runCatching {
        substring(start.coerceAtMost(length), end.coerceAtMost(length)).trim()
    }.getOrElse {
        throw ParseException("Feil i parsing av kravlinje: Startposisjon $start er større enn sluttposisjon $end")
    }

// Amounts use implicit 2-decimal encoding: "00000001234" → BigDecimal("12.34")
private fun String.getBigDecimal(start: Int, end: Int): BigDecimal {
    val amount = getString(start, end)
    return if (amount.length < 3) BigDecimal.ZERO
    else runCatching {
        BigDecimal("${amount.dropLast(2)}.${amount.takeLast(2)}")
    }.getOrElse { throw ParseException("Feil i parsing av BigDecimal ($start, $end): ${it.message}") }
}

private fun String.getInt(start: Int, end: Int): Int =
    runCatching { getString(start, end).toInt() }
        .getOrElse { throw ParseException("Feil i parsing av Int ($start, $end): ${it.message}") }

// Dates are "yyyyMMdd"; returns LineValidationRules.errorDate sentinel on parse failure
private fun String.getDate(start: Int, end: Int): LocalDate =
    runCatching {
        LocalDate.parse(getString(start, end), DateTimeFormatter.ofPattern("yyyyMMdd"))
    }.getOrDefault(LineValidationRules.errorDate)

// Returns null if the field is blank
private fun String.getOptionalDate(start: Int, end: Int): LocalDate? =
    if (getString(start, end).isBlank()) null else getDate(start, end)
```

### kravKode character replacement

The `kravKode` field may contain the Unicode replacement character `\uFFFD` where the source system encoded `Ø`. Replace it after extraction:

```kotlin
kravKode = getString(start = 75, end = 83).replace(0xFFFD.toChar(), 'Ø'),
```

## Boundaries

### ✅ Always

- Return `ValidationResult` from all validation functions — never throw for business-rule failures
- Use `buildList {}` to accumulate **all** errors before returning (do not short-circuit on first error)
- Include line number and field values in every error message string
- Set `status = Status.KRAV_IKKE_SENDT.value` on valid lines, `Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value` on invalid lines
- Call `slackService.sendErrors()` exactly once at the end of each validation pass
- Use `LineValidationRules.errorDate` as the sentinel value for unparseable date fields
- Replace `\uFFFD` → `Ø` in `kravKode` after extraction

### ⚠️ Ask First

- Adding new validation rules to `LineValidationRules` (may affect SKE acceptance rate)
- Changing byte offsets in `FileParser` (format is fixed by the upstream sender)
- Changing which `Avsender` values trigger `fagsystemId` validation

### 🚫 Never

- Throw from individual `check*` functions in `LineValidationRules` (return `null` for OK, `String` for error)
- Drop invalid lines from the returned list — always return all lines with status set
- Persist line errors after the `status` field has already been set to `VALIDERINGSFEIL_AV_LINJE_I_FIL`
- Modify any `KravLinje` fields other than `status` during validation

