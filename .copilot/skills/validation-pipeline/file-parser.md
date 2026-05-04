# FileParser — Fixed-Width Copybook Parser

`FileParser` uses positional `substring()` extraction (no delimiter). All positions are **0-based character offsets**.

## File structure

```
Line 1        KontrollLinjeHeader   sender (avsender) + transaction date
Lines 2..N-1  KravLinje             one claim per line, 200+ chars wide
Line N         KontrollLinjeFooter  record count + checksum sum
```

## Data classes

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

## Byte positions for KravLinje

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

## Private extraction helpers (extension functions on `String` inside `FileParser`)

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

## kravKode character replacement

The `kravKode` field may contain the Unicode replacement character `\uFFFD` where the source system encoded `Ø`. Replace it after extraction:

```kotlin
kravKode = getString(start = 75, end = 83).replace(0xFFFD.toChar(), 'Ø'),
```
