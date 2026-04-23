# File Validation Patterns

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
