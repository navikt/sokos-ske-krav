# Copilot Review Instructions – sokos-ske-krav

## False Positives to Suppress

### ParseException.message is non-nullable

`io.ktor.http.parsing.ParseException` declares `override val message: String` (non-nullable). Do NOT flag `e.message` as `String?` when the caught exception type is `ParseException`. Code like `add(e.message)` or `listOf(e.message)` where `e: ParseException` is type-safe and compiles without warnings.
