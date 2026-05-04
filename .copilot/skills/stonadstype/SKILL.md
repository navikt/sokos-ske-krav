---
name: stonadstype
description: "StonadsType enum-mapping fra (kravKode, kodeHjemmel) til stønadstyper med Identifikator, og sjekkliste for å legge til nye stønadstyper"
---

# StonadsType Enum & Adding New Entries

StonadsType enum: mapping av (kravKode, kodeHjemmel)-par til stønadstyper, og sjekkliste for å legge til nye.

> `StonadsType` maps NAV's internal `(kravKode, kodeHjemmel)` pairs to Skatteetaten's benefit type classification. Each enum entry holds one or more `Identifikator` values. The companion `getStonadstype()` performs the lookup and throws `NotImplementedError` for unknown combinations.

## Structure

```kotlin
enum class StonadsType(
    vararg val identifikatorer: Identifikator,
) {
    TILBAKEKREVING_ALDERSPENSJON(Identifikator("PE AP", "T")),
    // Some entries have multiple Identifikator mappings:
    TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER(
        Identifikator("AAP AAP", "T"),
        Identifikator("AE AA", "AT"),
    ),
    ;

    val kravKoder: List<String> get() = identifikatorer.map { it.kravKode }

    data class Identifikator(
        val kravKode: String,
        val kodeHjemmel: String,
    )

    companion object {
        fun getStonadstype(
            kravkode: String,
            kodeHjemmel: String,
        ): StonadsType =
            StonadsType.entries.firstOrNull { type ->
                type.identifikatorer.any { it.kravKode == kravkode && it.kodeHjemmel == kodeHjemmel }
            }
                ?: throw NotImplementedError(
                    "Kombinasjonen kravkode=$kravkode og hjemmelkode=$kodeHjemmel gir ingen stønadstype.",
                )
    }
}
```

## Where StonadsType is Used

| Location | Usage |
|---|---|
| `LineValidationRules.kravTypeIsValid()` | Validates that `(kravKode, kodeHjemmel)` maps to a known StonadsType |
| `CreateRequests.createOpprettKravRequest()` | Maps krav to `stonadstype` field in the SKE API request |
| `Application.kt` | Registers Prometheus metrics counters for each unique `kravKode` at startup |
| `RapportService` | Includes StonadsType in report/CSV output |

## Checklist: Adding a New StonadsType

### 1. Add the enum entry in `StonadsType.kt`

Add a new entry in **alphabetical order** among the existing entries. The naming convention is `TILBAKEKREVING_<YTELSESNAVN>`:

```kotlin
// Single Identifikator:
TILBAKEKREVING_NY_YTELSE(Identifikator("XX YY", "T")),

// Multiple Identifikator mappings (if multiple source systems send the same benefit):
TILBAKEKREVING_NY_YTELSE(
    Identifikator("XX YY", "T"),
    Identifikator("ZZ ZZ", "AT"),
),
```

- `kravKode`: The benefit code from the upstream system (e.g., `"PE AP"`, `"BA OR"`)
- `kodeHjemmel`: The legal basis code (e.g., `"T"` for tilbakekreving, `"TA"` for avregning, `"AT"` for Arena-tilbakekreving)

### 2. Add test mapping in `StonadsTypeTest.kt`

Add the new `(kravKode, kodeHjemmel) → StonadsType` mapping(s) to the `testCombinations` map:

```kotlin
val testCombinations = mapOf(
    // ... existing entries ...
    Pair("XX YY", "T") to StonadsType.TILBAKEKREVING_NY_YTELSE,
)
```

The test `"testdata skal dekke alle kravKode og kodeHjemmel kombinasjoner fra StonadsType"` **will fail** if you forget this step — it verifies that every `Identifikator` in the enum is covered by the test map.

### 3. No other code changes needed

- **Validation**: `LineValidationRules.kravTypeIsValid()` uses `getStonadstype()` — new entries are automatically accepted
- **Request creation**: `CreateRequests.createOpprettKravRequest()` uses `getStonadstype()` — new entries are automatically mapped
- **Metrics**: `Application.kt` iterates `StonadsType.entries.flatMap { it.kravKoder }` — new kravKoder are automatically registered

### 4. Verify

Run the StonadsType tests to confirm everything is wired up:

```bash
./gradlew test --tests "no.nav.sokos.ske.krav.service.unit.StonadsTypeTest"
```

## Boundaries

### ✅ Always

- Add entries in alphabetical order
- Use `TILBAKEKREVING_` prefix for enum names
- Add corresponding test entries in `StonadsTypeTest.kt`
- Verify with tests after adding

### ⚠️ Ask First

- Adding entries with new `kodeHjemmel` values not previously used (may need SKE coordination)
- Entries where the same `kravKode` maps to different StonadsType values with different `kodeHjemmel`

### 🚫 Never

- Remove or rename existing entries without coordinating with SKE
- Add duplicate `(kravKode, kodeHjemmel)` combinations across different entries
- Skip adding the test mapping — the test suite enforces full coverage
