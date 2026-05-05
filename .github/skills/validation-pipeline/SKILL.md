---
name: validation-pipeline
description: "Valideringspipeline med ValidationResult sealed class, FileValidator, LineValidationRules, LineValidator og FileParser for fixed-width copybook-filer"
---

# Validation & File Parsing Patterns

Valideringsmønstre: ValidationResult sealed class, FileValidator, LineValidationRules, LineValidator og FileParser for fixed-width filer.

> Validation is a two-phase pipeline: **file-level** (`FileValidator`) then **line-level** (`LineValidator` + `LineValidationRules`). Both produce `ValidationResult`. File errors move the whole file to `/inbound/feilfiler`; line errors mark individual lines as `VALIDERINGSFEIL_AV_LINJE_I_FIL` and keep them in the DB.

## ValidationResult sealed class

All validation functions return `ValidationResult`. Never throw exceptions for expected business-rule failures — return `ValidationResult.Error` instead:

```kotlin
sealed class ValidationResult {
    data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
    data class Error(val messages: List<Pair<String, String>>) : ValidationResult()
}
```

`messages` is a list of `(errorKey, humanReadableMessage)` pairs. The `errorKey` identifies the failure type (used as the Slack header/tag key); the human-readable message includes the field value and line number.

## Sub-files

- See [file-validation.md](file-validation.md) for FileValidator patterns (file-level rules, error keys, pattern).
- See [line-validation.md](line-validation.md) for LineValidationRules (per-line business rules) and LineValidator (orchestration).
- See [file-parser.md](file-parser.md) for FileParser (fixed-width copybook parser, data classes, byte positions, extraction helpers).

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
